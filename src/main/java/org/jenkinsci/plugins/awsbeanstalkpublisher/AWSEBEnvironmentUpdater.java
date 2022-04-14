package org.jenkinsci.plugins.awsbeanstalkpublisher;

import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Result;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.jenkinsci.plugins.awsbeanstalkpublisher.extensions.AWSEBSetup;
import org.jenkinsci.plugins.awsbeanstalkpublisher.extensions.AWSEBElasticBeanstalkSetup;
import org.jenkinsci.plugins.awsbeanstalkpublisher.extensions.AWSEBS3Setup;
import org.jenkinsci.plugins.awsbeanstalkpublisher.extensions.envlookup.EnvLookup;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.regions.Region;
import com.amazonaws.services.elasticbeanstalk.AWSElasticBeanstalk;
import com.amazonaws.services.elasticbeanstalk.model.EnvironmentDescription;

public class AWSEBEnvironmentUpdater {
    
    private final static int MAX_THREAD_COUNT = 5;
    
    private final AbstractBuild<?, ?> build;
    private final BuildListener listener;
    private final AWSEBElasticBeanstalkSetup envSetup;
    
    private final String applicationName;
    private final String versionLabel;
    private final AWSElasticBeanstalk awseb;
    private final boolean failOnError;
    
    
    public AWSEBEnvironmentUpdater(AbstractBuild<?, ?> build, Launcher launcher, 
            BuildListener listener, AWSEBElasticBeanstalkSetup envSetup){
        this.build = build;
        this.listener = listener;
        this.envSetup = envSetup;
        
        applicationName = AWSEBUtils.getValue(build, listener,envSetup.getApplicationName());
        versionLabel = AWSEBUtils.getValue(build, listener,envSetup.getVersionLabelFormat());
        failOnError = envSetup.getFailOnError();
        

        AWSEBCredentials credentials = envSetup.getActualcredentials(build, listener);
        AWSCredentialsProvider provider = null;
        if (credentials != null) {
            provider = envSetup.getActualcredentials(build, listener).getAwsCredentials();
        }
        
        Region region = Region.getRegion(envSetup.getAwsRegion(build, listener));
        
        awseb = AWSEBUtils.getElasticBeanstalk(provider, region);
    }
    
    public boolean perform() throws Exception{
        for (AWSEBSetup extension : envSetup.getExtensions()) {
            if (extension instanceof AWSEBS3Setup){
                AWSEBS3Setup s3 = (AWSEBS3Setup) extension;
                AWSEBS3Uploader uploader = new AWSEBS3Uploader(build, listener, envSetup, s3);
                uploader.uploadArchive(awseb);
            }
        }
        
        return updateEnvironments();
    }
    

    public boolean updateEnvironments() throws InterruptedException {
        List<EnvironmentDescription> envList = new ArrayList<>(10);
        
        for (AWSEBSetup extension : envSetup.getEnvLookup()) {
            if (extension instanceof EnvLookup){
                EnvLookup envLookup = (EnvLookup) extension;
                envList.addAll(envLookup.getEnvironments(build, listener, awseb, applicationName));
            }
        }
        
        if (envList.size() <= 0) {
            AWSEBUtils.log(listener, "No environments found matching applicationName:%s", 
                    applicationName);
            if (envSetup.getFailOnError()) {
                listener.finished(Result.FAILURE);
                return false;
            } else {
                listener.finished(Result.SUCCESS);
                return true;
            }
        }

        ExecutorService pool = Executors.newFixedThreadPool(MAX_THREAD_COUNT);

        List<AWSEBEnvironmentUpdaterThread> updaters = new ArrayList<>();
        for (EnvironmentDescription envd : envList) {
            AWSEBUtils.log(listener, "Environment found (environment id='%s', name='%s'). "
                    + "Attempting to update environment to version label '%s'", 
                    envd.getEnvironmentId(), envd.getEnvironmentName(), versionLabel);
            updaters.add(new AWSEBEnvironmentUpdaterThread(awseb, envd, listener, versionLabel));
        }
        List<Future<AWSEBEnvironmentUpdaterThread>> results = pool.invokeAll(updaters);

        return printResults(results);
    }

    private boolean printResults(List<Future<AWSEBEnvironmentUpdaterThread>> results) {
        PrintStream log = listener.getLogger();
        boolean allSuccess = true;
        for (Future<AWSEBEnvironmentUpdaterThread> future : results) {
            try {
                AWSEBEnvironmentUpdaterThread result = future.get();
                allSuccess &= result.isSuccessfull();
                result.printResults();
            } catch (Exception e) {
                AWSEBUtils.log(listener, "Unable to get results from update");
                e.printStackTrace(log);
            }
        }
        if (failOnError && !allSuccess) {
            listener.finished(Result.FAILURE);
            build.setResult(Result.FAILURE);
            return false;
        } else {
            listener.finished(Result.SUCCESS);
            return true;
        }
    }

}

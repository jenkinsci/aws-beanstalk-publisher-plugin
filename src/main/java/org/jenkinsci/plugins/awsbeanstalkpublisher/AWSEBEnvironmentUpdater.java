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

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.regions.Region;
import com.amazonaws.services.elasticbeanstalk.AWSElasticBeanstalk;
import com.amazonaws.services.elasticbeanstalk.model.DescribeEnvironmentsRequest;
import com.amazonaws.services.elasticbeanstalk.model.DescribeEnvironmentsResult;
import com.amazonaws.services.elasticbeanstalk.model.EnvironmentDescription;

public class AWSEBEnvironmentUpdater {

    private final static int MAX_THREAD_COUNT = 5;

    private final AbstractBuild<?, ?> build;
    private final BuildListener listener;
    private final PrintStream log;
    private final AWSEBElasticBeanstalkSetup envSetup;

    private final List<String> environments;
    private final String applicationName;
    private final String versionLabel;
    private final AWSElasticBeanstalk awseb;
    private final boolean failOnError;


    public AWSEBEnvironmentUpdater(AbstractBuild<?, ?> build, Launcher launcher,
            BuildListener listener, AWSEBElasticBeanstalkSetup envSetup){
        this.build = build;
        this.listener = listener;
        this.log = listener.getLogger();
        this.envSetup = envSetup;

        environments = AWSEBUtils.getValue(build, envSetup.getEnvironments());
        applicationName = AWSEBUtils.getValue(build, envSetup.getApplicationName());
        versionLabel = AWSEBUtils.getValue(build, envSetup.getVersionLabelFormat());
        failOnError = envSetup.getFailOnError();


        AWSCredentialsProvider provider = envSetup.getCredentials().getAwsCredentials();
        Region region = Region.getRegion(envSetup.getAwsRegion());

        awseb = AWSEBUtils.getElasticBeanstalk(provider, region);
    }

    public void perform() throws Exception{
        for (AWSEBSetup extension : envSetup.getExtensions()) {
            if (extension instanceof AWSEBS3Setup){
                AWSEBS3Setup s3 = (AWSEBS3Setup) extension;
                AWSEBS3Uploader uploader = new AWSEBS3Uploader(build, listener, envSetup, s3);
                uploader.uploadArchive(awseb);
            }
        }

        updateEnvironments();
    }

    public void updateEnvironments() {
        DescribeEnvironmentsRequest request = new DescribeEnvironmentsRequest();
        request.setApplicationName(applicationName);
        request.setIncludeDeleted(false);

        if (environments != null && !environments.isEmpty()) {
            request.setEnvironmentNames(environments);
        }
        try {
            updateEnvironments(request);
        } catch (Exception e) {
            e.printStackTrace(log);
        }
    }

    public void updateEnvironments(DescribeEnvironmentsRequest request) throws InterruptedException {
        DescribeEnvironmentsResult result = awseb.describeEnvironments(request);

        List<EnvironmentDescription> envList = result.getEnvironments();

        if (envList.size() <= 0) {
            AWSEBUtils.log(log, "No environments found matching applicationName:%s with environments:%s",
                    applicationName, environments);
            listener.finished(Result.FAILURE);
            return;
        }

        ExecutorService pool = Executors.newFixedThreadPool(MAX_THREAD_COUNT);

        List<AWSEBEnvironmentUpdaterThread> updaters = new ArrayList<AWSEBEnvironmentUpdaterThread>();
        for (EnvironmentDescription envd : envList) {
            AWSEBUtils.log(log, "Environment found (environment id='%s', name='%s'). "
                    + "Attempting to update environment to version label '%s'",
                    envd.getEnvironmentId(), envd.getEnvironmentName(), versionLabel);
            updaters.add(new AWSEBEnvironmentUpdaterThread(awseb, request, envd, log, versionLabel));
        }
        List<Future<AWSEBEnvironmentUpdaterThread>> results = pool.invokeAll(updaters);

        printResults(listener, results);
    }

    private void printResults(BuildListener listener, List<Future<AWSEBEnvironmentUpdaterThread>> results) {
        PrintStream log = listener.getLogger();
        boolean hadFailures = false;
        for (Future<AWSEBEnvironmentUpdaterThread> future : results) {
            try {
                AWSEBEnvironmentUpdaterThread result = future.get();
                hadFailures |= result.isSuccessfull();
                result.printResults();
            } catch (Exception e) {
                AWSEBUtils.log(log, "Unable to get results from update");
                e.printStackTrace(log);
            }
        }
        if (failOnError && hadFailures) {
            listener.finished(Result.FAILURE);
        } else {
            listener.finished(Result.SUCCESS);
        }
    }

}

package org.jenkinsci.plugins.awsbeanstalkpublisher;

import hudson.Util;
import hudson.model.BuildListener;
import hudson.model.AbstractBuild;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import org.apache.commons.lang.StringUtils;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.elasticbeanstalk.AWSElasticBeanstalk;
import com.amazonaws.services.elasticbeanstalk.AWSElasticBeanstalkClient;
import com.amazonaws.services.elasticbeanstalk.model.ApplicationDescription;
import com.amazonaws.services.elasticbeanstalk.model.DescribeApplicationsResult;
import com.amazonaws.services.elasticbeanstalk.model.DescribeEnvironmentsRequest;
import com.amazonaws.services.elasticbeanstalk.model.DescribeEnvironmentsResult;
import com.amazonaws.services.elasticbeanstalk.model.EnvironmentDescription;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;

public class AWSEBUtils {

    private final static Pattern ENV_NAME_REGEX = Pattern.compile("([a-zA-Z0-9][-a-zA-Z0-9]{2,21}[a-zA-Z0-9]|\\$\\{.*\\})");

    private static final Logger logger = Logger.getLogger(AWSEBUtils.class.getName());
    
    public static String formatPath(String mask, Object... args) {
        return strip(String.format(mask, args).replaceAll("/{2,}", ""));
    }

    public static List<String> getValue(AbstractBuild<?, ?> build, BuildListener listener, List<String> values) {
        List<String> newValues = new ArrayList<>(values.size());
        for (String value : values) {
            if (!value.isEmpty()) {
                newValues.add(getValue(build, listener, value));
            }
        }
        return newValues;
    }
    
    public static AmazonS3 getS3(AWSEBCredentials credentials, Regions awsRegion) {
        AWSCredentialsProvider provider = null;
        if (credentials != null) {
            provider = credentials.getAwsCredentials();
        }
        Region region = Region.getRegion(awsRegion);

        AmazonS3 s3 = region.createClient(AmazonS3Client.class, provider, AWSEBUtils.getClientConfig());
        return s3;
    }
    
    public static String getValue(AbstractBuild<?, ?> build, BuildListener listener, String value) {
        return strip(replaceMacros(build, listener, value));
    }

    public static String strip(String str) {
        return StringUtils.strip(str, "/ ");
    }
    
    
    public static String getApplicationListAsString(AWSEBCredentials credentials, Regions region) {
        AWSCredentialsProvider awsCredentials = null;
        if (credentials != null) {
            awsCredentials = credentials.getAwsCredentials();
        }
        List<ApplicationDescription> apps = getApplications(awsCredentials, region);
        
        StringBuilder sb = new StringBuilder();
        for (ApplicationDescription app : apps) {
            sb.append(app.getApplicationName());
            sb.append("\n");
        }
        return sb.toString();
    }

    public static List<String> getBadEnvironmentNames(String environments) {
        List<String> badEnv = new ArrayList<>();
        if (environments != null && !environments.isEmpty()) {

            for (String env : environments.split("\n")) {
                if (!isValidEnvironmentName(env)) {
                    badEnv.add(env);
                }
            }
        }
        return badEnv;
    }

    public static boolean isValidEnvironmentName(String name) {
        return ENV_NAME_REGEX.matcher(name).matches();
    }

    public static List<ApplicationDescription> getApplications(AWSCredentialsProvider credentials, Regions region) {
        AWSElasticBeanstalk awseb = getElasticBeanstalk(credentials, Region.getRegion(region));
        DescribeApplicationsResult result = awseb.describeApplications();
        return result.getApplications();
    }

    public static List<EnvironmentDescription> getEnvironments(AWSCredentialsProvider credentials, Regions region, String appName) {
        AWSElasticBeanstalk awseb = getElasticBeanstalk(credentials, Region.getRegion(region));

        DescribeEnvironmentsRequest request = new DescribeEnvironmentsRequest().withApplicationName(appName);

        DescribeEnvironmentsResult result = awseb.describeEnvironments(request);
        return result.getEnvironments();
    }

    public static String replaceMacros(AbstractBuild<?, ?> build, BuildListener listener, String inputString) {
        String returnString = inputString;
        if (build != null && inputString != null) {
            try {
                Map<String, String> messageEnvVars = new HashMap<>();

                messageEnvVars.putAll(build.getCharacteristicEnvVars());
                messageEnvVars.putAll(build.getBuildVariables());
                messageEnvVars.putAll(build.getEnvironment(listener));

                returnString = Util.replaceMacro(inputString, messageEnvVars);

            } catch (Exception e) {
                logger.log(Level.SEVERE, "Couldn't replace macros in message: ", e);
            }
        }
        return returnString;

    }
    
    public static AWSElasticBeanstalk getElasticBeanstalk(AWSCredentialsProvider credentials, Region region) {
        AWSElasticBeanstalk awseb = region.createClient(AWSElasticBeanstalkClient.class, credentials, getClientConfig());
        return awseb;
    }
    
    public static ClientConfiguration getClientConfig() {
        ClientConfiguration clientConfig = new ClientConfiguration();
        clientConfig.setUserAgent(ClientConfiguration.DEFAULT_USER_AGENT);
        return clientConfig;
    }
    

    public static void log(BuildListener listener, String mask, Object... args) {
        listener.getLogger().println(String.format(mask, args));
    }

}

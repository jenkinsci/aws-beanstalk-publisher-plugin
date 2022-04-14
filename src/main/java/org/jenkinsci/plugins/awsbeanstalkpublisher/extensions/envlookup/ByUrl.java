package org.jenkinsci.plugins.awsbeanstalkpublisher.extensions.envlookup;

import hudson.Extension;
import hudson.model.BuildListener;
import hudson.model.AbstractBuild;
import hudson.util.FormValidation;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.awsbeanstalkpublisher.AWSEBCredentials;
import org.jenkinsci.plugins.awsbeanstalkpublisher.AWSEBUtils;
import org.jenkinsci.plugins.awsbeanstalkpublisher.extensions.AWSEBSetup;
import org.jenkinsci.plugins.awsbeanstalkpublisher.extensions.AWSEBSetupDescriptor;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.elasticbeanstalk.AWSElasticBeanstalk;
import com.amazonaws.services.elasticbeanstalk.model.DescribeEnvironmentsRequest;
import com.amazonaws.services.elasticbeanstalk.model.DescribeEnvironmentsResult;
import com.amazonaws.services.elasticbeanstalk.model.EnvironmentDescription;

public class ByUrl extends AWSEBSetup implements EnvLookup {

    private final List<String> urlList;

    @DataBoundConstructor
    public ByUrl(String urlList) {
        this.urlList = new ArrayList<>();
        if (!StringUtils.isEmpty(urlList)) {
            for (String next : urlList.split("\n")) {
                this.urlList.add(next.trim());
            }
        }
    }

    public String getUrlList() {
        return urlList == null ? "" : StringUtils.join(urlList, '\n');
    }

    @Override
    public List<EnvironmentDescription> getEnvironments(AbstractBuild<?, ?> build, BuildListener listener, AWSElasticBeanstalk awseb, String applicationName) {
        DescribeEnvironmentsRequest request = new DescribeEnvironmentsRequest();
        request.withApplicationName(applicationName);
        request.withIncludeDeleted(false);

        DescribeEnvironmentsResult result = awseb.describeEnvironments(request);

        List<EnvironmentDescription> environments = new ArrayList<>();

        List<String> resolvedUrls = new ArrayList<>(urlList.size());

        for (String url : urlList) {
            resolvedUrls.add(AWSEBUtils.replaceMacros(build, listener, url));
        }

        for (EnvironmentDescription environment : result.getEnvironments()) {
            String envUrl = environment.getCNAME();
            if (resolvedUrls.contains(envUrl)) {
                environments.add(environment);
            }
        }

        return environments;
    }

    @Extension
    public static class DescriptorImpl extends AWSEBSetupDescriptor {
        @Override
        public String getDisplayName() {
            return "Get Environments by Url";
        }
        

        public FormValidation doLoadEnvironments(
                @QueryParameter("credentialsString") String credentialsString, 
                @QueryParameter("credentialsText") String credentialsText, 
                @QueryParameter("awsRegion") String awsRegion, 
                @QueryParameter("awsRegionText") String awsRegionText,
                @QueryParameter("applicationName") String appName) {
            AWSEBCredentials credentials = AWSEBCredentials.getCredentialsByString(credentialsString);
            if (credentials == null) {
                credentials = AWSEBCredentials.getCredentialsByString(credentialsText);
            }
            Regions region = Enum.valueOf(Regions.class, awsRegion);
            if (region == null) {
                region = Enum.valueOf(Regions.class, awsRegionText);
                if (region == null) {
                    return FormValidation.error("Missing valid Region");
                }
            }

            if (appName == null) {
                return FormValidation.error("Missing an application name");
            }

            return FormValidation.ok(getEnvironmentCnamesListAsString(credentials, region, appName));
        }
        

        public static String getEnvironmentCnamesListAsString(AWSEBCredentials credentials, Regions region, String appName) {
            AWSCredentialsProvider awsCredentials = null;
            if (credentials != null) {
                awsCredentials = credentials.getAwsCredentials();
            }
            List<EnvironmentDescription> environments = AWSEBUtils.getEnvironments(awsCredentials, region, appName);
            StringBuilder sb = new StringBuilder();
            for (EnvironmentDescription env : environments) {
                sb.append(env.getCNAME());
                sb.append("\n");
            }
            return sb.toString();
        }

    }

}

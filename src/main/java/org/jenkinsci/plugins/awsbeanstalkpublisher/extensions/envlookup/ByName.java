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
import com.amazonaws.services.elasticbeanstalk.model.EnvironmentDescription;

public class ByName extends AWSEBSetup implements EnvLookup {

    private final List<String> envNameList;

    @DataBoundConstructor
    public ByName(String envNameList) {
        this.envNameList = new ArrayList<String>();
        if (!StringUtils.isEmpty(envNameList)) {
            for (String next : envNameList.split("\n")) {
                this.envNameList.add(next.trim());
            }
        }
    }

    public String getEnvNameList() {
        return envNameList == null ? "" : StringUtils.join(envNameList, '\n');
    }

    @Override
    public List<EnvironmentDescription> getEnvironments(AbstractBuild<?, ?> build, BuildListener listener, AWSElasticBeanstalk awseb, String applicationName) {
        DescribeEnvironmentsRequest request = new DescribeEnvironmentsRequest();
        request.withApplicationName(applicationName);
        request.withIncludeDeleted(false);
        
        List<String> escaped = new ArrayList<String>(envNameList.size());
        for (String env : envNameList) {
            escaped.add(AWSEBUtils.replaceMacros(build, listener, env));
        }

        request.withEnvironmentNames(escaped);

        return awseb.describeEnvironments(request).getEnvironments();
    }

    @Extension
    public static class DescriptorImpl extends AWSEBSetupDescriptor {
        @Override
        public String getDisplayName() {
            return "Get Environments By Name";
        }
        

        public FormValidation doLoadEnvironments(
                @QueryParameter("credentialsString") String credentialsString, 
                @QueryParameter("credentialsText") String credentialsText, 
                @QueryParameter("awsRegion") String awsRegion, 
                @QueryParameter("awsRegionText") String awsRegionText,
                @QueryParameter("applicationName") String appName) {
            AWSEBCredentials credentials = AWSEBCredentials.getCredentialsByString(credentialsString);
            if (credentials == null) {
                credentials = AWSEBCredentials.getCredentialsByString(credentialsString);
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

            return FormValidation.ok(getEnvironmentsListAsString(credentials, region, appName));
        }
        

        private String getEnvironmentsListAsString(AWSEBCredentials credentials, Regions region, String appName) {
            AWSCredentialsProvider awsCredentials = null;
            if (credentials != null) {
                awsCredentials = credentials.getAwsCredentials();
            }
            List<EnvironmentDescription> environments = AWSEBUtils.getEnvironments(awsCredentials, region, appName);
            StringBuilder sb = new StringBuilder();
            for (EnvironmentDescription env : environments) {
                sb.append(env.getEnvironmentName());
                sb.append("\n");
            }
            return sb.toString();
        }
    }

}

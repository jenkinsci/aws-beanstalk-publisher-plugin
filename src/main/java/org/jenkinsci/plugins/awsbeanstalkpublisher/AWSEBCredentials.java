package org.jenkinsci.plugins.awsbeanstalkpublisher;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.AWSCredentialsProviderChain;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.internal.StaticCredentialsProvider;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.elasticbeanstalk.model.ApplicationDescription;

import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.model.ModelObject;
import hudson.util.FormValidation;

public class AWSEBCredentials extends AbstractDescribableImpl<AWSEBCredentials> implements ModelObject {

    @Extension
    public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();

    private final String name;
    private final String awsAccessKeyId;
    private final String awsSecretSharedKey;

    private final static Set<AWSEBCredentials> credentials = new HashSet<AWSEBCredentials>();

    public String getName() {
        return name;
    }

    public String getAwsAccessKeyId() {
        return awsAccessKeyId;
    }

    public String getAwsSecretSharedKey() {
        return awsSecretSharedKey;
    }


    public Regions getAwsRegion() {
        return Regions.DEFAULT_REGION;
    }

    @DataBoundConstructor
    public AWSEBCredentials(String name, String awsAccessKeyId, String awsSecretSharedKey) {
        this.name = name;
        this.awsAccessKeyId = awsAccessKeyId;
        this.awsSecretSharedKey = awsSecretSharedKey;
    }

    public String getDisplayName() {
        return name + " : " + awsAccessKeyId;
    }

    public AWSCredentialsProvider getAwsCredentials() {
        AWSCredentialsProvider credentials = new AWSCredentialsProviderChain(new StaticCredentialsProvider(new BasicAWSCredentials(getAwsAccessKeyId(), getAwsSecretSharedKey())));
        return credentials;
    }

    public static void configureCredentials(Collection<AWSEBCredentials> toAdd) {
        credentials.clear();
        credentials.addAll(toAdd);
    }

    public static Set<AWSEBCredentials> getCredentials() {
        return credentials;
    }

    public static AWSEBCredentials getCredentialsByString(String credentialsString) {
        Set<AWSEBCredentials> credentials = getCredentials();

        for (AWSEBCredentials credential : credentials) {
            if (credential.getName().equals(credentialsString) || credential.toString().equals(credentialsString)) {
                return credential;
            }
        }

        return null;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof AWSEBCredentials)) {
            return false;
        }
        AWSEBCredentials creds = (AWSEBCredentials) o;
        boolean isSame = this.awsAccessKeyId.equals(creds.awsAccessKeyId);
        isSame &= this.name.equals(creds.name);
        return isSame;
    }

    @Override
    public String toString() {
        return name + " : " + awsAccessKeyId;
    }

    @Override
    public int hashCode() {
        return (awsAccessKeyId).hashCode();
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return DESCRIPTOR;
    }


    public final static class DescriptorImpl extends Descriptor<AWSEBCredentials> {

        @Override
        public String getDisplayName() {
            return "Credentials for Amazon Web Service";
        }

        public FormValidation doLoadApplications(@QueryParameter("awsAccessKeyId") String accessKey, @QueryParameter("awsSecretSharedKey") String secretKey, @QueryParameter("awsRegion") String regionString) {
            if (accessKey == null || secretKey == null) {
                return FormValidation.error("Access key and Secret key cannot be empty");
            }
            AWSEBCredentials credentials = new AWSEBCredentials("", accessKey, secretKey);
            Regions region = Enum.valueOf(Regions.class, regionString);
            if (region == null) {
                return FormValidation.error("Missing valid Region");
            }

            List<ApplicationDescription> apps = AWSEBUtils.getApplications(credentials.getAwsCredentials(), region);


            StringBuilder sb = new StringBuilder();
            for (ApplicationDescription app : apps) {
                sb.append(app.getApplicationName());
                sb.append("\n");
            }
            return FormValidation.ok(sb.toString());
        }

    }
}

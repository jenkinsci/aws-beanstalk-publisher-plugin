package org.jenkinsci.plugins.awsbeanstalkpublisher.extensions;

import hudson.Extension;

import java.util.ArrayList;
import java.util.List;

import org.kohsuke.stapler.DataBoundConstructor;

public class AWSEBS3Setup extends AWSEBSetup {

    @Extension
    public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();

    @DataBoundConstructor
    public AWSEBS3Setup(String bucketName, String bucketRegion, String keyPrefix, String rootObject, String includes, String excludes, Boolean overwriteExistingFile, Boolean useTransferAcceleration) {
        this.bucketName = bucketName;
        this.bucketRegion = bucketRegion;
        this.keyPrefix = keyPrefix;
        this.rootObject = rootObject;
        this.overwriteExistingFile = overwriteExistingFile == null ? false : overwriteExistingFile;
        this.includes = includes;
        this.excludes = excludes;
        this.useTransferAcceleration = useTransferAcceleration ==null ? false:useTransferAcceleration;
    }

    /**
     * Bucket Name
     */
    private final String bucketName;

    public String getBucketName() {
        return bucketName;
    }

    /**
     * Bucket Region
     */
    private final String bucketRegion;

    public String getBucketRegion() {
        return bucketRegion;
    }

    /**
     * Key Format
     */
    private final String keyPrefix;

    public String getKeyPrefix() {
        return keyPrefix;
    }

    private final String rootObject;

    public String getRootObject() {
        return rootObject;
    }

    private final String includes;

    public String getIncludes() {
        return includes;
    }

    private final String excludes;

    public String getExcludes() {
        return excludes;
    }

    private final Boolean overwriteExistingFile;

    public boolean isOverwriteExistingFile() {
        return (overwriteExistingFile == null ? false : overwriteExistingFile);
    }

    private final Boolean useTransferAcceleration;

    public Boolean isUseTransferAcceleration() {
        return useTransferAcceleration == null ? false : useTransferAcceleration;
    }

    // Overridden for better type safety.
    // If your plugin doesn't really define any property on Descriptor,
    // you don't have to do this.
    @Override
    public DescriptorImpl getDescriptor() {
        return DESCRIPTOR;
    }



    @Extension
    public static class DescriptorImpl extends AWSEBSetupDescriptor {
        @Override
        public String getDisplayName() {
            return "Deploy to S3";
        }

        public List<AWSEBSetupDescriptor> getExtensionDescriptors() {
            final List<AWSEBSetupDescriptor> extensions = new ArrayList<>(1);
            extensions.add(AWSEBS3Setup.DESCRIPTOR);
            return extensions;
        }
    }
}

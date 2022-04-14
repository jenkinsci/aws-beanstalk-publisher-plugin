package org.jenkinsci.plugins.awsbeanstalkpublisher.extensions;

import hudson.DescriptorExtensionList;
import hudson.model.Descriptor;
import jenkins.model.Jenkins;

public abstract class AWSEBSetupDescriptor extends Descriptor<AWSEBSetup> {
    public boolean isApplicable(Class<?> type) {
        return true;
    }

    public static DescriptorExtensionList<AWSEBSetup,AWSEBSetupDescriptor> all() {
        return Jenkins.get().getDescriptorList(AWSEBSetup.class);
    }
}

package org.jenkinsci.plugins.awsbeanstalkpublisher;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import hudson.Extension;
import hudson.Launcher;
import hudson.Util;
import hudson.init.InitMilestone;
import hudson.init.Initializer;
import hudson.model.BuildListener;
import hudson.model.Items;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Result;
import hudson.model.Saveable;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;
import hudson.util.DescribableList;
import net.sf.json.JSONObject;

import org.jenkinsci.plugins.awsbeanstalkpublisher.extensions.AWSEBElasticBeanstalkSetup;
import org.jenkinsci.plugins.awsbeanstalkpublisher.extensions.AWSEBSetup;
import org.jenkinsci.plugins.awsbeanstalkpublisher.extensions.AWSEBSetupDescriptor;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

/**
 * AWS Elastic Beanstalk Deployment
 */
public class AWSEBPublisher extends AWSEBPublisherBackwardsCompatibility {
    
    @Initializer(before=InitMilestone.PLUGINS_STARTED)
    public static void addAlias() {
        Items.XSTREAM2.addCompatibilityAlias("org.jenkinsci.plugins.awsbeanstalkpublisher.AWSEBDeploymentPublisher", AWSEBPublisher.class);
    }
    
    @DataBoundConstructor
    public AWSEBPublisher(
            List<AWSEBElasticBeanstalkSetup> extensions) {
        super();
        this.extensions = new DescribableList<>(
                Saveable.NOOP,Util.fixNull(extensions));
    }
    
    private DescribableList<AWSEBSetup, AWSEBSetupDescriptor> extensions;
    
    public DescribableList<AWSEBSetup, AWSEBSetupDescriptor> getExtensions() {
        if (extensions == null) {
            extensions = new DescribableList<>(Saveable.NOOP,Util.fixNull(extensions));
        }
        return extensions;
    }
    

    public Object readResolve() {
        readBackExtensionsFromLegacy();
        return this;
    }

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) {
        if (build.getResult().isWorseThan(Result.SUCCESS)) {
            return false;
        }
        return AWSEBSetup.perform(build, launcher, listener, getExtensions());
    }

    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }

    // Overridden for better type safety.
    // If your plugin doesn't really define any property on Descriptor,
    // you don't have to do this.
    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {

        private Set<AWSEBCredentials> credentials;

        @SuppressWarnings("rawtypes")
        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            // Indicates that this builder can be used with all kinds of project types
            return true;
        }

        /**
         * This human readable name is used in the configuration screen.
         */
        public String getDisplayName() {
            return "Deploy into AWS Elastic Beanstalk";
        }

        
        public List<AWSEBSetupDescriptor> getExtensionDescriptors() {
            List<AWSEBSetupDescriptor> extensions = new ArrayList<>(1);
            extensions.add(AWSEBElasticBeanstalkSetup.getDesc());
            return extensions;
        }

        public DescriptorImpl() {
            load();
            if (credentials != null) {
                AWSEBCredentials.configureCredentials(credentials);
            } else if (AWSEBCredentials.getCredentials() != null) {
                credentials = AWSEBCredentials.getCredentials();
            }
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject json) throws FormException {
            
            AWSEBCredentials.configureCredentials(req.bindJSONToList(AWSEBCredentials.class, json.get("credentials")));
            credentials = AWSEBCredentials.getCredentials();
            save();
            return super.configure(req, json);
        }
        
        public Set<AWSEBCredentials> getCredentials() {
            return credentials;
        }
        
    }

}

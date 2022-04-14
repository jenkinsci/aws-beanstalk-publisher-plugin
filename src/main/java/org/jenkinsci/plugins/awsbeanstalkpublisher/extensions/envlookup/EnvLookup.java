package org.jenkinsci.plugins.awsbeanstalkpublisher.extensions.envlookup;

import java.util.List;

import hudson.model.BuildListener;
import hudson.model.AbstractBuild;

import com.amazonaws.services.elasticbeanstalk.AWSElasticBeanstalk;
import com.amazonaws.services.elasticbeanstalk.model.EnvironmentDescription;

public interface EnvLookup {
    
    List<EnvironmentDescription> getEnvironments(AbstractBuild<?, ?> build, BuildListener listener, AWSElasticBeanstalk awseb, String applicationName);

}

package org.jenkinsci.plugins.awsbeanstalkpublisher;


import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.FreeStyleBuild;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class DeployerTest {
	
	@Mock
	private FreeStyleBuild build;
	
	@Mock 
	private Launcher launcher;
	
	@Mock
	private BuildListener listener;

	@Test
	public void test() throws Exception {
		
	}
}

package org.jenkinsci.plugins.awsbeanstalkpublisher;

import hudson.model.BuildListener;

import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import com.amazonaws.services.elasticbeanstalk.AWSElasticBeanstalk;
import com.amazonaws.services.elasticbeanstalk.model.DescribeEnvironmentsRequest;
import com.amazonaws.services.elasticbeanstalk.model.DescribeEnvironmentsResult;
import com.amazonaws.services.elasticbeanstalk.model.DescribeEventsRequest;
import com.amazonaws.services.elasticbeanstalk.model.DescribeEventsResult;
import com.amazonaws.services.elasticbeanstalk.model.EnvironmentDescription;
import com.amazonaws.services.elasticbeanstalk.model.EventDescription;
import com.amazonaws.services.elasticbeanstalk.model.UpdateEnvironmentRequest;

public class AWSEBEnvironmentUpdaterThread implements Callable<AWSEBEnvironmentUpdaterThread> {
    private static final int MAX_ATTEMPTS = 5;
    private static final int WAIT_TIME_SECONDS = 30;
    private static final long WAIT_TIME_MILLISECONDS = TimeUnit.SECONDS.toMillis(WAIT_TIME_SECONDS);
    
    private final EnvironmentDescription envd;
    private final AWSElasticBeanstalk awseb;
    private final DescribeEnvironmentsRequest envRequest;
    private final DescribeEventsRequest eventRequest;
    private final String environmentId;
    private final BuildListener listener;
    private final String versionLabel;

    private boolean isUpdated = false;
    private boolean isComplete = false;
    private boolean success = false;
    private int nAttempt;
    private EventDescription lastEvent;

    public AWSEBEnvironmentUpdaterThread(AWSElasticBeanstalk awseb, EnvironmentDescription envd, BuildListener listener, String versionLabel) {
        this.awseb = awseb;
        this.envd = envd;
        this.listener = listener;
        this.versionLabel = versionLabel;
        this.lastEvent = new EventDescription();
        lastEvent.setEventDate(new Date());
        
        // We can make our requests and, hopefully, safely assume the environmentId won't change under us.
        envRequest = new DescribeEnvironmentsRequest().withEnvironmentIds(envd.getEnvironmentId());
        eventRequest = new DescribeEventsRequest().withEnvironmentId(envd.getEnvironmentId());
        
        // Hack to acknowledge that the time of the Jenkins box may not match AWS.
        try {
            DescribeEventsResult lastEntry = awseb.describeEvents(new DescribeEventsRequest()
                                                                        .withEnvironmentId(envd.getEnvironmentId())
                                                                        .withMaxRecords(1));
            lastEvent = lastEntry.getEvents().get(0);
        } catch (Exception e) {
            log("'%s': Unable to get last event, using system current timestamp for event logs", envd.getEnvironmentName());
        }
        eventRequest.withStartTime(lastEvent.getEventDate()); // Initialize to the right start time.
        
        this.environmentId = envd.getEnvironmentId();
        nAttempt = 0;

    }

    private void log(String mask, Object... args) {
        listener.getLogger().println(String.format(mask, args));
    }

    private void updateEnv() {
        
        log("'%s': Attempt %d/%d", envd.getEnvironmentName(), nAttempt, MAX_ATTEMPTS);
        
        
        UpdateEnvironmentRequest uavReq = new UpdateEnvironmentRequest().withEnvironmentId(environmentId).withVersionLabel(versionLabel);
        isUpdated = true;
        

        try {
            awseb.updateEnvironment(uavReq);
            isReady();
            nAttempt = 0;
        } catch (Exception e) {
            log("'%s': Problem:", envd.getEnvironmentName());
            e.printStackTrace(listener.getLogger());
            if (e.getMessage().contains("No Application Version named")) {
                isComplete = true;
            }

            if (nAttempt++ > MAX_ATTEMPTS) {
                log("'%s': Unable to update environment!", envd.getEnvironmentName());
                isComplete = true;
            }

        }
    }
    
    private boolean compareEventDescriptions(EventDescription first, EventDescription second) {
        boolean isEqual = first.getApplicationName().equals(second.getApplicationName());
        isEqual &= first.getEnvironmentName().equals(second.getEnvironmentName());
        isEqual &= first.getMessage().equals(second.getMessage());
        isEqual &= first.getSeverity().equals(second.getSeverity());
        isEqual &= first.getEventDate().getTime() == second.getEventDate().getTime();
        return isEqual;
    }

    private void isReady() {
        try {
            String envName = envd.getEnvironmentName();
            
            try {
                // Using start time so we only get logs after the last event.
                eventRequest.withStartTime(lastEvent.getEventDate());
                DescribeEventsResult eventResult = awseb.describeEvents(eventRequest);
                SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss zZ");
                
                List<EventDescription> events = eventResult.getEvents();
                
                // Reverse the logs so we print them in order of earliest to latest, following jenkins logs.
                Collections.reverse(events);
                
                // Remove last event so we don't get duplicates, hopefully.
                if (compareEventDescriptions(lastEvent, events.get(0))) {
                    events.remove(0);
                }
                
                // Set the last event date.
                lastEvent = events.get(0);
                
                
                for (EventDescription event : events) {
                    Date eventDate = event.getEventDate();
                    // 2015-04-13 20:12:44 UTC-0600
                    String eventDateString = dateFormat.format(eventDate);
                    log("'%s': EVENT [%s] (%s) %s", envName, eventDateString, event.getSeverity(), event.getMessage());
                }
            } catch (Exception e) {
                log("'%s': Unable to process events %s", envName, e.getMessage());
            }

            DescribeEnvironmentsResult result = awseb.describeEnvironments(envRequest);
            EnvironmentDescription lastEnv = null;
            for (EnvironmentDescription env : result.getEnvironments()) {
                if (env.getEnvironmentId().equals(envd.getEnvironmentId())){
                    lastEnv = env;
                    break;
                }
            }
            if (lastEnv == null) {
                isComplete = true;
                log("'%s' is no longer found in ElasticBeanstalk!!!!", envName);
                return;
            }
            if (lastEnv.getStatus().equals("Ready")) {
                isComplete = true;
                
                log("'%s': Updated!", envName);
                log("'%s': Current version is:'%s'", envName, lastEnv.getVersionLabel());
                
                if (lastEnv.getVersionLabel().equals(versionLabel)) {
                    success = true;
                    log("'%s': Update was successful", envName);
                } else {
                    success = false;
                    log("'%s': Update failed, please check the recent events on the AWS console!!!!", envName);
                }
            } else {
                log("'%s': Waiting for update to finish. Status: %s", envName, lastEnv.getStatus());
            }
        } catch (Exception e) {

            log("Problem: " + e.getMessage());

            if (nAttempt++ > MAX_ATTEMPTS) {
                log("'%s': unable to get environment status.", envd.getEnvironmentName());
                isComplete = true;
            }
        }
    }

    @Override
    public AWSEBEnvironmentUpdaterThread call() throws Exception {
        run();
        return this;
    }
    
    public void printResults() {
        StringBuilder status = new StringBuilder();
        status.append("'");
        status.append(envd.getEnvironmentName());
        status.append("': ");
        if (success) {
            status.append("Completed successfully.");
        } else {
            if (isUpdated) {
                status.append("Was updated, but couldn't be verified!");
            } else {
                status.append("Failed to be updated!!");
            }
        }
        log(status.toString());
    }
    
    public boolean isSuccessfull() {
        return success;
    }

    public void run() {
        while (!isComplete) {
            if (isUpdated) {
                isReady();
            } else {
                updateEnv();
            }
            if (!isComplete){
                try {
                    log("'%s': Pausing update for %d seconds", envd.getEnvironmentName(), WAIT_TIME_SECONDS);
                    Thread.sleep(WAIT_TIME_MILLISECONDS);
                } catch (Exception e) {
                    e.printStackTrace(listener.getLogger());
                }
            }
        }
    }
}
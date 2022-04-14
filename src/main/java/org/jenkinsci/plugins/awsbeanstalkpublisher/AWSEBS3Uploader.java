package org.jenkinsci.plugins.awsbeanstalkpublisher;

import com.amazonaws.regions.Regions;
import com.amazonaws.services.elasticbeanstalk.AWSElasticBeanstalk;
import com.amazonaws.services.elasticbeanstalk.model.CreateApplicationVersionRequest;
import com.amazonaws.services.elasticbeanstalk.model.S3Location;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.S3ClientOptions;
import com.amazonaws.services.s3.model.AmazonS3Exception;
import com.amazonaws.services.s3.model.GetBucketAccelerateConfigurationRequest;
import com.amazonaws.services.s3.model.ObjectMetadata;
import hudson.FilePath;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.util.DirScanner;
import org.apache.commons.codec.digest.DigestUtils;
import org.jenkinsci.plugins.awsbeanstalkpublisher.extensions.AWSEBElasticBeanstalkSetup;
import org.jenkinsci.plugins.awsbeanstalkpublisher.extensions.AWSEBS3Setup;

import java.io.*;
import java.util.concurrent.TimeUnit;

public class AWSEBS3Uploader {

    
    private final String keyPrefix;
    private final String bucketName;
    private final String bucketRegion;
    private final String includes;
    private final String excludes;
    private final String rootObject;
    private final boolean isOverwriteExistingFile;
    private final boolean useTransferAcceleration;
    
    private final String applicationName;
    private final String versionLabel;
    private final String versionDescription;
    private final Regions awsRegion;
    private final AbstractBuild<?, ?> build;
    private final BuildListener listener;
    private final AWSEBCredentials credentials;
    

    private String objectKey;
    private String s3ObjectPath;
    private AmazonS3 s3;

    public AWSEBS3Uploader(AbstractBuild<?, ?> build, BuildListener listener, Regions awsRegion,
            AWSEBCredentials credentials, AWSEBS3Setup s3Setup,
            String applicationName, String versionLabel, String versionDescription) {
        this.credentials = credentials;
        this.build = build;
        this.awsRegion = awsRegion;
        this.listener = listener;
        this.applicationName = AWSEBUtils.getValue(build, listener, applicationName);
        this.versionLabel = AWSEBUtils.getValue(build, listener, versionLabel);
        this.versionDescription = AWSEBUtils.getValue(build, listener, versionDescription);
        this.keyPrefix = AWSEBUtils.getValue(build, listener, s3Setup.getKeyPrefix());
        this.bucketName = AWSEBUtils.getValue(build, listener, s3Setup.getBucketName());
        this.bucketRegion = AWSEBUtils.getValue(build, listener, s3Setup.getBucketRegion());
        this.includes = AWSEBUtils.getValue(build, listener, s3Setup.getIncludes());
        this.excludes = AWSEBUtils.getValue(build, listener, s3Setup.getExcludes());
        this.rootObject = AWSEBUtils.getValue(build, listener, s3Setup.getRootObject());
        this.isOverwriteExistingFile = s3Setup.isOverwriteExistingFile();
        this.useTransferAcceleration = s3Setup.isUseTransferAcceleration();
    }


    public AWSEBS3Uploader(AbstractBuild<?, ?> build, BuildListener listener, AWSEBElasticBeanstalkSetup envSetup, AWSEBS3Setup s3) {
        this(build, listener, envSetup.getAwsRegion(build, listener), envSetup.getActualcredentials(build, listener), s3, envSetup.getApplicationName(), envSetup.getVersionLabelFormat(), envSetup.getVersionDescriptionFormat());
    }


    public void uploadArchive(AWSElasticBeanstalk awseb) throws Exception {
        if (s3 == null) {
            // Check whether we should use the env region or the bucket one.
            if(this.bucketRegion.isEmpty())
                s3 = AWSEBUtils.getS3(credentials, awsRegion);
            else
                s3 = AWSEBUtils.getS3(credentials, Regions.fromName(bucketRegion));
        }

        objectKey = AWSEBUtils.formatPath("%s/%s-%s.zip", keyPrefix, applicationName, versionLabel);

        s3ObjectPath = "s3://" + AWSEBUtils.formatPath("%s/%s", bucketName, objectKey);
        FilePath rootFileObject = new FilePath(build.getWorkspace(), AWSEBUtils.getValue(build, listener, rootObject));
        File localArchive = getLocalFileObject(rootFileObject);

        AWSEBUtils.log(listener, "Uploading file %s as %s", localArchive.getName(), s3ObjectPath);

        boolean uploadFile = true;

        try {
            ObjectMetadata meta = s3.getObjectMetadata(bucketName, objectKey);
            String awsMd5 = meta.getContentMD5();
            FileInputStream fis = new FileInputStream(localArchive);
            String ourMd5 = DigestUtils.md5Hex(fis);
            fis.close();
            if (ourMd5.equals(awsMd5)) {
                uploadFile = false || isOverwriteExistingFile;
            }
        } catch (AmazonS3Exception s3e) {
            if (s3e.getStatusCode() == 403 || s3e.getStatusCode() == 404) {
                // i.e. 404: NoSuchKey - The specified key does not exist
                // 403: PermissionDenied is a sneaky way to hide that the file doesn't exist
                uploadFile = true;
            } else {
                throw s3e;
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace(listener.getLogger());
        } catch (IOException e) {
            e.printStackTrace(listener.getLogger());
        }

        //see if the bucket is enabled for Acceleration:
        if (useTransferAcceleration) {
            if (s3.getBucketAccelerateConfiguration(new GetBucketAccelerateConfigurationRequest(bucketName)).isAccelerateEnabled()) {
                s3.setS3ClientOptions(S3ClientOptions.builder().setAccelerateModeEnabled(true).build());
                AWSEBUtils.log(listener, "Bucket %s configured for Transfer Acceleration!", bucketName);
            }
            else {
                AWSEBUtils.log(listener, "Bucket %s does not support Transfer Acceleration", bucketName);
            }
        }

        if (uploadFile) {
            long startTimeNanos = System.nanoTime();
            s3.putObject(bucketName, objectKey, localArchive);
            long finishTimeNanos = System.nanoTime();
            long timeElapsedMillis = TimeUnit.MILLISECONDS.convert(finishTimeNanos - startTimeNanos, TimeUnit.NANOSECONDS);
            AWSEBUtils.log(listener, "Upload took " + timeElapsedMillis + " milliseconds");
        }
        localArchive.delete();
        createApplicationVersion(awseb);
    }

    // Visible for testing
    void setS3(AmazonS3 s3) {
        this.s3 = s3;
    }

    private File getLocalFileObject(FilePath rootFileObject) throws Exception {
        File resultFile = File.createTempFile("awseb-", ".zip");

        if (!rootFileObject.isDirectory()) {
            AWSEBUtils.log(listener, "Root File Object is a file. We assume its a zip file, which is okay.");

            rootFileObject.copyTo(new FileOutputStream(resultFile));
        } else {
            AWSEBUtils.log(listener, "Zipping contents of Root File Object (%s) into tmp file %s (includes=%s, excludes=%s)", rootFileObject.getName(), resultFile.getName(), includes, excludes);

            rootFileObject.zip(new FileOutputStream(resultFile), new DirScanner.Glob(includes, excludes));
        }

        return resultFile;
    }

    private void createApplicationVersion(AWSElasticBeanstalk awseb) {
        AWSEBUtils.log(listener, "Creating application version %s for application %s for path %s", versionLabel, applicationName, s3ObjectPath);

        CreateApplicationVersionRequest cavRequest = new CreateApplicationVersionRequest()
                .withApplicationName(applicationName)
                .withAutoCreateApplication(true)
                .withSourceBundle(new S3Location(bucketName, objectKey))
                .withVersionLabel(versionLabel)
                .withDescription(versionDescription);

        awseb.createApplicationVersion(cavRequest);
    }


}

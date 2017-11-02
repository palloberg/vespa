// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.deployment;

import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Environment;
import com.yahoo.test.ManualClock;
import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.ApplicationController;
import com.yahoo.vespa.hosted.controller.ConfigServerClientMock;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.ControllerTester;
import com.yahoo.vespa.hosted.controller.api.identifiers.TenantId;
import com.yahoo.vespa.hosted.controller.api.integration.BuildService;
import com.yahoo.vespa.hosted.controller.application.ApplicationPackage;
import com.yahoo.vespa.hosted.controller.application.Change;
import com.yahoo.vespa.hosted.controller.application.DeploymentJobs;
import com.yahoo.vespa.hosted.controller.application.DeploymentJobs.JobType;
import com.yahoo.vespa.hosted.controller.maintenance.FailureRedeployer;
import com.yahoo.vespa.hosted.controller.maintenance.JobControl;
import com.yahoo.vespa.hosted.controller.maintenance.Upgrader;
import com.yahoo.vespa.hosted.controller.versions.VersionStatus;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * This class provides convenience methods for testing deployments
 *
 * @author bratseth
 * @author mpolden
 */
public class DeploymentTester {

    // Set a long interval so that maintainers never do scheduled runs during tests
    private static final Duration maintenanceInterval = Duration.ofDays(1);

    private final ControllerTester tester;
    private final Upgrader upgrader;
    private final FailureRedeployer failureRedeployer;

    public DeploymentTester() {
        this(new ControllerTester());
    }

    public DeploymentTester(ControllerTester tester) {
        this.tester = tester;
        tester.curator().writeUpgradesPerMinute(100);
        this.upgrader = new Upgrader(tester.controller(), maintenanceInterval, new JobControl(tester.curator()),
                                     tester.curator());
        this.failureRedeployer = new FailureRedeployer(tester.controller(), maintenanceInterval,
                                                       new JobControl(tester.curator()));
    }

    public Upgrader upgrader() { return upgrader; }

    public FailureRedeployer failureRedeployer() { return failureRedeployer; }

    public Controller controller() { return tester.controller(); }

    public ApplicationController applications() { return tester.controller().applications(); }

    public BuildSystem buildSystem() { return tester.controller().applications().deploymentTrigger().buildSystem(); }

    public DeploymentTrigger deploymentTrigger() { return tester.controller().applications().deploymentTrigger(); }

    public ManualClock clock() { return tester.clock(); }

    public ControllerTester controllerTester() { return tester; }

    public ConfigServerClientMock configServer() { return tester.configServer(); }

    public Application application(String name) {
        return application(ApplicationId.from("tenant1", name, "default"));
    }

    public Application application(ApplicationId application) {
        return controller().applications().require(application);
    }

    public Optional<Change.VersionChange> versionChange(ApplicationId application) {
        return application(application).deploying()
                .filter(c -> c instanceof Change.VersionChange)
                .map(Change.VersionChange.class::cast);
    }

    public void updateVersionStatus() {
        controller().updateVersionStatus(VersionStatus.compute(controller(), tester.controller().systemVersion()));
    }
    
    public void updateVersionStatus(Version currentVersion) {
        controller().updateVersionStatus(VersionStatus.compute(controller(), currentVersion));
    }

    public void upgradeSystem(Version version) {
        controllerTester().configServer().setDefaultVersion(version);
        updateVersionStatus(version);
        upgrader().maintain();
    }

    public Application createApplication(String applicationName, String tenantName, long projectId, Long propertyId) {
        TenantId tenant = tester.createTenant(tenantName, UUID.randomUUID().toString(), propertyId);
        return tester.createApplication(tenant, applicationName, "default", projectId);
    }

    public void restartController() { tester.createNewController(); }

    /** Simulate the full lifecycle of an application deployment as declared in given application package */
    public Application createAndDeploy(String applicationName, int projectId, ApplicationPackage applicationPackage) {
        tester.createTenant("tenant1", "domain1", 1L);
        Application application = tester.createApplication(new TenantId("tenant1"), applicationName, "default", projectId);
        deployCompletely(application, applicationPackage);
        return applications().require(application.id());
    }

    /** Simulate the full lifecycle of an application deployment to prod.us-west-1 with the given upgrade policy */
    public Application createAndDeploy(String applicationName, int projectId, String upgradePolicy) {
        return createAndDeploy(applicationName, projectId, applicationPackage(upgradePolicy));
    }

    /** Complete an ongoing deployment */
    public void deployCompletely(String applicationName) {
        deployCompletely(applications().require(ApplicationId.from("tenant1", applicationName, "default")),
                         applicationPackage("default"));
    }

    /** Deploy application completely using the given application package */
    public void deployCompletely(Application application, ApplicationPackage applicationPackage) {
        notifyJobCompletion(JobType.component, application, true);
        assertTrue(applications().require(application.id()).deploying().isPresent());
        completeDeployment(application, applicationPackage, Optional.empty(), true);
    }

    /** Deploy application using the given application package, but expecting to stop after test phases */
    public void deployTestOnly(Application application, ApplicationPackage applicationPackage) {
        notifyJobCompletion(JobType.component, application, true);
        assertTrue(applications().require(application.id()).deploying().isPresent());
        completeDeployment(application, applicationPackage, Optional.empty(), false);
    }

    private void completeDeployment(Application application, ApplicationPackage applicationPackage, 
                                    Optional<JobType> failOnJob, boolean includingProductionZones) {
        DeploymentOrder order = new DeploymentOrder(controller());
        List<JobType> jobs = order.jobsFrom(applicationPackage.deploymentSpec());
        if ( ! includingProductionZones)
            jobs = jobs.stream().filter(job -> ! job.isProduction()).collect(Collectors.toList());
        for (JobType job : jobs) {
            boolean failJob = failOnJob.map(j -> j.equals(job)).orElse(false);
            deployAndNotify(application, applicationPackage, !failJob, false, job);
            if (failJob) {
                break;
            }
        }
        if (failOnJob.isPresent()) {
            assertTrue(applications().require(application.id()).deploying().isPresent());
            assertTrue(applications().require(application.id()).deploymentJobs().hasFailures());
        } else if (includingProductionZones) {
            assertFalse(applications().require(application.id()).deploying().isPresent());
        }
        else {
            assertTrue(applications().require(application.id()).deploying().isPresent());
        }
    }

    public void notifyJobCompletion(JobType jobType, Application application, boolean success) {
        notifyJobCompletion(jobType, application, DeploymentJobs.JobError.from(success));
    }

    public void notifyJobCompletion(JobType jobType, Application application, Optional<DeploymentJobs.JobError> jobError) {
        applications().notifyJobCompletion(jobReport(application, jobType, jobError));
    }

    public void completeUpgrade(Application application, Version version, String upgradePolicy) {
        assertTrue(applications().require(application.id()).deploying().isPresent());
        assertEquals(new Change.VersionChange(version), applications().require(application.id()).deploying().get());
        completeDeployment(application, applicationPackage(upgradePolicy), Optional.empty(), true);
    }

    public void completeUpgradeWithError(Application application, Version version, String upgradePolicy, JobType failOnJob) {
        completeUpgradeWithError(application, version, applicationPackage(upgradePolicy), Optional.of(failOnJob));
    }

    public void completeUpgradeWithError(Application application, Version version, ApplicationPackage applicationPackage, JobType failOnJob) {
        completeUpgradeWithError(application, version, applicationPackage, Optional.of(failOnJob));
    }

    private void completeUpgradeWithError(Application application, Version version, ApplicationPackage applicationPackage, Optional<JobType> failOnJob) {
        assertTrue(applications().require(application.id()).deploying().isPresent());
        assertEquals(new Change.VersionChange(version), applications().require(application.id()).deploying().get());
        completeDeployment(application, applicationPackage, failOnJob, true);
    }

    public void deploy(JobType job, Application application, ApplicationPackage applicationPackage) {
        deploy(job, application, applicationPackage, false);
    }

    public void deploy(JobType job, Application application, ApplicationPackage applicationPackage, boolean deployCurrentVersion) {
        job.zone(controller().system()).ifPresent(zone -> tester.deploy(application, zone, applicationPackage, deployCurrentVersion));
    }

    public void deployAndNotify(Application application, ApplicationPackage applicationPackage, boolean success,
                                JobType... jobs) {
        deployAndNotify(application, applicationPackage, success, true, jobs);
    }

    public void deployAndNotify(Application application, ApplicationPackage applicationPackage, boolean success, 
                                boolean expectOnlyTheseJobs, JobType... jobs) {
        consumeJobs(application, expectOnlyTheseJobs, jobs);
        for (JobType job : jobs) {
            if (success) {
                deploy(job, application, applicationPackage);
            }
            notifyJobCompletion(job, application, success);
        }
    }

    /** Assert that the sceduled jobs of this application are exactly those given, and take them */
    private void consumeJobs(Application application, boolean expectOnlyTheseJobs, JobType... jobs) {
        for (JobType job : jobs) {
            Optional<BuildService.BuildJob> buildJob = findJob(application, job);
            assertTrue(String.format("Job %s is scheduled for %s", job, application), buildJob.isPresent());
            assertEquals((long) application.deploymentJobs().projectId().get(), buildJob.get().projectId());
            assertEquals(job.id(), buildJob.get().jobName());
        }
        if (expectOnlyTheseJobs)
            assertEquals(jobs.length, countJobsOf(application));
        buildSystem().removeJobs(application.id());
    }

    private Optional<BuildService.BuildJob> findJob(Application application, JobType jobType) {
        for (BuildService.BuildJob job : buildSystem().jobs())
            if (job.projectId() == application.deploymentJobs().projectId().get() && job.jobName().equals(jobType.id()))
                return Optional.of(job);
        return Optional.empty();
    }

    private int countJobsOf(Application application) {
        return (int)buildSystem().jobs().stream()
                                        .filter(job -> job.projectId() == application.deploymentJobs().projectId().get())
                                        .count();
    }
    private DeploymentJobs.JobReport jobReport(Application application, JobType jobType, Optional<DeploymentJobs.JobError> jobError) {
        return new DeploymentJobs.JobReport(
                application.id(),
                jobType,
                application.deploymentJobs().projectId().get(),
                42,
                jobError
        );
    }

    private static ApplicationPackage applicationPackage(String upgradePolicy) {
        return new ApplicationPackageBuilder()
                .upgradePolicy(upgradePolicy)
                .environment(Environment.prod)
                .region("us-west-1")
                .build();
    }

}

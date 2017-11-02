// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi;

import com.yahoo.application.container.JDisc;
import com.yahoo.application.container.handler.Request;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Zone;
import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.TestIdentities;
import com.yahoo.vespa.hosted.controller.api.Tenant;
import com.yahoo.vespa.hosted.controller.api.application.v4.model.DeployOptions;
import com.yahoo.vespa.hosted.controller.api.application.v4.model.GitRevision;
import com.yahoo.vespa.hosted.controller.api.application.v4.model.ScrewdriverBuildJob;
import com.yahoo.vespa.hosted.controller.api.identifiers.AthenzDomain;
import com.yahoo.vespa.hosted.controller.api.identifiers.GitBranch;
import com.yahoo.vespa.hosted.controller.api.identifiers.GitCommit;
import com.yahoo.vespa.hosted.controller.api.identifiers.GitRepository;
import com.yahoo.vespa.hosted.controller.api.identifiers.Property;
import com.yahoo.vespa.hosted.controller.api.identifiers.PropertyId;
import com.yahoo.vespa.hosted.controller.api.identifiers.ScrewdriverId;
import com.yahoo.vespa.hosted.controller.api.identifiers.TenantId;
import com.yahoo.vespa.hosted.controller.api.identifiers.UserId;
import com.yahoo.vespa.hosted.controller.application.ApplicationPackage;
import com.yahoo.vespa.hosted.controller.application.DeploymentJobs;
import com.yahoo.vespa.hosted.controller.athenz.AthenzPrincipal;
import com.yahoo.vespa.hosted.controller.athenz.mock.AthenzDbMock;
import com.yahoo.vespa.hosted.controller.athenz.mock.AthenzClientFactoryMock;
import com.yahoo.vespa.hosted.controller.maintenance.JobControl;
import com.yahoo.vespa.hosted.controller.maintenance.Upgrader;
import com.yahoo.vespa.hosted.controller.persistence.CuratorDb;
import com.yahoo.vespa.hosted.controller.persistence.MockCuratorDb;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.Optional;

/**
 * Provides testing of controller functionality accessed through the container
 * 
 * @author bratseth
 */
public class ContainerControllerTester {

    private final ContainerTester containerTester;
    private final Controller controller;
    private final Upgrader upgrader;

    public ContainerControllerTester(JDisc container, String responseFilePath) {
        containerTester = new ContainerTester(container, responseFilePath);
        controller = (Controller)container.components().getComponent("com.yahoo.vespa.hosted.controller.Controller");
        CuratorDb curatorDb = new MockCuratorDb();
        curatorDb.writeUpgradesPerMinute(100);
        upgrader = new Upgrader(controller, Duration.ofDays(1), new JobControl(curatorDb), curatorDb);
    }

    public Controller controller() { return controller; }

    public Upgrader upgrader() { return upgrader; }

    /** Returns the wrapped generic container tester */
    public ContainerTester containerTester() { return containerTester; }

    public Application createApplication() {
        return createApplication("domain1","tenant1",
                                 "application1");
    }

    public Application createApplication(String athensDomain, String tenant, String application) {
        AthenzDomain domain1 = addTenantAthenzDomain(athensDomain, "mytenant");
        controller.tenants().addTenant(Tenant.createAthensTenant(new TenantId(tenant), domain1,
                                                                 new Property("property1"),
                                                                 Optional.of(new PropertyId("1234"))),
                                       Optional.of(TestIdentities.userNToken));
        ApplicationId app = ApplicationId.from(tenant, application, "default");
        return controller.applications().createApplication(app, Optional.of(TestIdentities.userNToken));
    }

    public Application deploy(Application application, ApplicationPackage applicationPackage, Zone zone, long projectId) {
        ScrewdriverId app1ScrewdriverId = new ScrewdriverId(String.valueOf(projectId));
        GitRevision app1RevisionId = new GitRevision(new GitRepository("repo"), new GitBranch("master"), new GitCommit("commit1"));
        controller.applications().deployApplication(application.id(),
                                                    zone,
                                                    applicationPackage,
                                                    new DeployOptions(Optional.of(new ScrewdriverBuildJob(app1ScrewdriverId, app1RevisionId)), Optional.empty(), false, false));
        return application;
    }

    public void notifyJobCompletion(ApplicationId applicationId, long projectId, boolean success, DeploymentJobs.JobType job) {
        controller().applications().notifyJobCompletion(new DeploymentJobs.JobReport(applicationId, job, projectId,
                                                                                     42,
                                                                                     success ? Optional.empty() : Optional.of(DeploymentJobs.JobError.unknown)
        ));
    }

    public AthenzDomain addTenantAthenzDomain(String domainName, String userName) {
        AthenzClientFactoryMock mock = (AthenzClientFactoryMock) containerTester.container().components()
                .getComponent(AthenzClientFactoryMock.class.getName());
        AthenzDomain athensDomain = new AthenzDomain(domainName);
        AthenzDbMock.Domain domain = new AthenzDbMock.Domain(athensDomain);
        domain.markAsVespaTenant();
        domain.admin(new AthenzPrincipal(new AthenzDomain("domain"), new UserId(userName)));
        mock.getSetup().addDomain(domain);
        return athensDomain;
    }

    // ---- Delegators:
    
    public void assertResponse(Request request, File expectedResponse) throws IOException {
        containerTester.assertResponse(request, expectedResponse);
    }

    public void assertResponse(Request request, String expectedResponse, int expectedStatusCode) throws IOException {
        containerTester.assertResponse(request, expectedResponse, expectedStatusCode);
    }

}

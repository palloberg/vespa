// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.api;

import com.yahoo.config.FileReference;
import com.yahoo.vespa.defaults.Defaults;

import java.io.File;
import java.util.Collection;
import java.util.Set;

/**
 * Interface for models towards filedistribution.
 *
 * @author lulf
 * @since 5.1
 */
public interface FileDistribution {

    void sendDeployedFiles(String hostName, Set<FileReference> fileReferences);
    void reloadDeployFileDistributor();
    // TODO: Remove when 6.150 is the oldest version used
    void limitSendingOfDeployedFilesTo(Collection<String> hostNames);
    void removeDeploymentsThatHaveDifferentApplicationId(Collection<String> targetHostnames);

    static File getDefaultFileDBPath() {
        return new File(Defaults.getDefaults().underVespaHome("var/db/vespa/filedistribution"));
    }

}

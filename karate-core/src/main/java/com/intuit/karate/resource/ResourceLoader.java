package com.intuit.karate.resource;

import java.io.File;
import java.util.Optional;

/**
 * A resource loader is used by karate to load feature and java script files.
 * This extracted concept enables to load features from any location.
 * This can be a database, a http server or anything else.
 *
 * I order to get your personal resource loader to work, you have to register
 * the resource loader using the ResourceLoaderRegistry. That all.
 */
public interface ResourceLoader {

    /**
     *
     *
     * @param workingDir physical working directory.
     * @param path path, url or an identification string to the resource. Could be an primary key of an data base entry if the resource should be loaded from a database for instance
     * @return an empty optional if the resource could not be loaded, the loaded resource else
     */
    Optional<Resource> getResource(File workingDir, String path);
}

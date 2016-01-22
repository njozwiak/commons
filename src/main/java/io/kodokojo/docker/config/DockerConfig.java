package io.kodokojo.docker.config;

/*
 * #%L
 * docker-image-manager
 * %%
 * Copyright (C) 2016 Kodo-kojo
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */

import io.kodokojo.docker.utils.properties.Key;
import io.kodokojo.docker.utils.properties.PropertyConfig;

public interface DockerConfig extends PropertyConfig {

    @Key("DOCKER_HOST")
    String dockerServerUrl();

    @Key("DOCKER_CERT_PATH")
    String dockerCertPath();

    @Key("DOCKER_TLS_VERIFY")
    String dockerTlsVerify();

    @Key("docker.registry.url")
    String dockerRegistryUrl();

}
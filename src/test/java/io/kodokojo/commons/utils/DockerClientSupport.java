package io.kodokojo.commons.utils;

/*
 * #%L
 * commons-image-manager
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

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.Ports;
import com.github.dockerjava.api.model.Version;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.command.PullImageResultCallback;
import com.squareup.okhttp.*;
import io.kodokojo.commons.config.DockerConfig;
import io.kodokojo.commons.utils.properties.PropertyResolver;
import io.kodokojo.commons.utils.properties.provider.DockerConfigValueProvider;
import io.kodokojo.commons.utils.properties.provider.SystemEnvValueProvider;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.apache.commons.lang.StringUtils.isBlank;

public class DockerClientSupport {

    private static final Logger LOGGER = LoggerFactory.getLogger(DockerClientSupport.class);

    private DockerClient dockerClient;

    private List<String> containerToClean;

    private String remoteDaemonDockerIp;

    private final boolean dockerIsPresent;



    public DockerClientSupport(DockerClientConfig config) {
        dockerClient = DockerClientBuilder.getInstance(config).build();
        remoteDaemonDockerIp = config.getUri() != null ? config.getUri().getHost() : "127.0.0.1";
        containerToClean = new ArrayList<>();
        dockerIsPresent = isDockerWorking();
    }

    public DockerClientSupport() {
        this(createDockerConfigFromProperties());
    }

    public boolean isDockerIsPresent() {
        return dockerIsPresent;
    }

    private static DockerClientConfig createDockerConfigFromProperties() {
        PropertyResolver propertyResolver = new PropertyResolver(new DockerConfigValueProvider(new SystemEnvValueProvider()));
        DockerConfig dockerConfig = propertyResolver.createProxy(DockerConfig.class);
        if (StringUtils.isBlank(dockerConfig.dockerServerUrl())) {
            return DockerClientConfig.createDefaultConfigBuilder().build();
        }
        return DockerClientConfig.createDefaultConfigBuilder().withDockerCertPath(dockerConfig.dockerCertPath()).withUri(dockerConfig.dockerServerUrl()).build();
    }

    public void addContainerIdToClean(String id) {
        containerToClean.add(id);
    }

    public void pullImage(String image) {
        if (isBlank(image)) {
            throw new IllegalArgumentException("image must be defined.");
        }
        if (dockerClient == null) {
            throw new IllegalArgumentException("dockerClient must be defined.");
        }
        try {
            dockerClient.pullImageCmd(image).exec(new PullImageResultCallback()).awaitCompletion().onComplete();
        } catch (InterruptedException e) {
            throw new RuntimeException("Unable to pull java image", e);
        }
    }


    public String getContainerName(String containerId) {
        InspectContainerResponse inspectContainerResponse = dockerClient.inspectContainerCmd(containerId).exec();
        return inspectContainerResponse.getName();
    }

    public int getExposedPort(String containerId, int containerPort) {
        InspectContainerResponse inspectContainerResponse = dockerClient.inspectContainerCmd(containerId).exec();
        Map<ExposedPort, Ports.Binding[]> bindings = inspectContainerResponse.getNetworkSettings().getPorts().getBindings();
        Ports.Binding[] bindingsExposed = bindings.get(ExposedPort.tcp(containerPort));
        if (bindingsExposed == null) {
            return -1;
        }
        return bindingsExposed[0].getHostPort();
    }

    public String getHttpContainerUrl(String containerId, int containerPort) {
        StringBuilder sb = new StringBuilder();
        sb.append("http://").append(getServerIp()).append(":").append(getExposedPort(containerId, containerPort));
        return sb.toString();
    }

    public void stopAndRemoveContainer() {
        boolean remove = !System.getProperty("docker.no.kill.container", "false").equals("true");
        if (remove) {
            containerToClean.forEach(id -> {
                if (remove) {
                    dockerClient.stopContainerCmd(id).exec();
                    dockerClient.removeContainerCmd(id).exec();
                    LOGGER.debug("Stopped and removed container id {}", id);
                } else {
                    LOGGER.warn("You ask us to not stop and remove containers. Ignore container id {}", id);
                }
            });
            containerToClean.clear();
        }
    }

    public DockerClient getDockerClient() {
        return dockerClient;
    }

    public boolean isDockerWorking() {
        return !isNotWorking(dockerClient);
    }

    private boolean isNotWorking(DockerClient dockerClient) {
        if (dockerClient == null) {
            return true;
        }
        try {
            Version version = dockerClient.versionCmd().exec();

            return version == null || StringUtils.isBlank(version.getGitCommit());
        } catch (Exception e) {
            return true;
        }
    }

    public String getServerIp() {
        return remoteDaemonDockerIp;
    }

    public boolean waitUntilHttpRequestRespond(String url, int time) {
        return waitUntilHttpRequestRespond(url, time, null);
    }

    public boolean waitUntilHttpRequestRespond(String url, int time, TimeUnit unit) {
        if (isBlank(url)) {
            throw new IllegalArgumentException("url must be defined.");
        }

        long now = System.currentTimeMillis();
        long delta = unit != null ? TimeUnit.MILLISECONDS.convert(time, unit) : time;
        long endTime = now + delta;
        long until = 0;


        OkHttpClient httpClient = new OkHttpClient();
        HttpUrl httpUrl = HttpUrl.parse(url);

        int nbTry = 0;
        boolean available = false;
        do {
            nbTry++;
            available = tryRequest(httpUrl, httpClient);
            if (!available) {
                try {
                    Thread.sleep(20);
                } catch (InterruptedException e) {
                    Thread.interrupted();
                    break;
                }
                now = System.currentTimeMillis();
                until = endTime - now;
            }
        } while (until > 0 && !available);
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug(url + " " + (available ? "Success" : "Failed after " + nbTry + " try"));
        }
        return available;
    }

    private boolean tryRequest(HttpUrl url, OkHttpClient httpClient) {
        Response response = null;
        try {
            Request request = new Request.Builder().url(url).get().build();
            Call call = httpClient.newCall(request);
            response = call.execute();
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("try Request {} , get response : {}", url.toString(), response);
            }
            boolean isSuccesseful = response.isSuccessful();
            response.body().close();
            return isSuccesseful;
        } catch (IOException e) {
            return false;
        } finally {
            if (response != null) {
                try {
                    response.body().close();
                } catch (IOException e) {
                    return false;
                }
            }
        }
    }

}
/*
 * Copyright 2013-2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.cloudfoundry.operations.services;

import lombok.Builder;
import lombok.Data;
import lombok.Singular;

import java.util.List;

/**
 * A service instance
 */
@Data
public final class ServiceInstance {

    /**
     * The dashboard Url
     *
     * @param dashboard the dashboard
     * @return the dashboard
     */
    private final String dashboard; //TODO: String?

    /**
     * The service description
     *
     * @param description the service description
     * @return the service description
     */
    private final String description;

    /**
     * The documentation URL
     *
     * @param documentationUrl the documentation URL
     * @return the documentation URL
     */
    private final String documentationUrl; //TODO: String?

    /**
     * The message ///TODO: ???
     *
     * @param message the message
     * @return the message
     */
    private final String message;

    /**
     * The managed service plan
     *
     * @param plan the managed service plan
     * @return the managed service plan
     */
    private final String plan;

    /**
     * The service - either the name of the managed service, or an indicator of 'user-provided'
     *
     * @param service the service
     * @return the service
     */
    private final String service;

    /**
     * The service instance
     *
     * @param serviceInstance the service instance
     * @return the service instance
     */
    private final String serviceInstance;

    /**
     * When the service was last started
     *
     * @param startedAt the started timestamp
     * @return the started timestamp
     */
    private final String startedAt;

    /**
     * The status of the last operation
     *
     * @param status the status of the last operation
     * @return the status of the last operation
     */
    private final String status;

    /**
     * The tags for the service
     *
     * @param tags the tags for the service
     * @return the tags for the service
     */
    private final List<String> tags;

    /**
     * The type of the service instance
     *
     * @param type the type of the service instance
     * @return the type of the service instance
     */
    private final ServiceInstanceType type;

    /**
     * When the service was last updated
     *
     * @param updatedAt the updated timestamp
     * @return the updated timestamp
     */
    private final String updatedAt;

    @Builder
    ServiceInstance(String dashboardUrl,
                    String description,
                    String documentationUrl,
                    String message,
                    String plan,
                    String service,
                    String serviceInstance,
                    String startedAt,
                    String status,
                    @Singular List<String> tags,
                    ServiceInstanceType type,
                    String updatedAt) {
        this.dashboard = dashboardUrl;
        this.description = description;
        this.documentationUrl = documentationUrl;
        this.message = message;
        this.plan = plan;
        this.service = service;
        this.serviceInstance = serviceInstance;
        this.startedAt = startedAt;
        this.status = status;
        this.tags = tags;
        this.type = type;
        this.updatedAt = updatedAt;
    }

}

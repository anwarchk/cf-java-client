/*
 * Copyright 2013-2017 the original author or authors.
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

package org.cloudfoundry;

import org.cloudfoundry.client.CloudFoundryClient;
import org.cloudfoundry.client.v2.applications.ApplicationResource;
import org.cloudfoundry.client.v2.applications.DeleteApplicationRequest;
import org.cloudfoundry.client.v2.applications.ListApplicationServiceBindingsRequest;
import org.cloudfoundry.client.v2.applications.ListApplicationsRequest;
import org.cloudfoundry.client.v2.applications.RemoveApplicationServiceBindingRequest;
import org.cloudfoundry.client.v2.buildpacks.DeleteBuildpackRequest;
import org.cloudfoundry.client.v2.buildpacks.ListBuildpacksRequest;
import org.cloudfoundry.client.v2.featureflags.ListFeatureFlagsRequest;
import org.cloudfoundry.client.v2.featureflags.ListFeatureFlagsResponse;
import org.cloudfoundry.client.v2.featureflags.SetFeatureFlagRequest;
import org.cloudfoundry.client.v2.organizationquotadefinitions.DeleteOrganizationQuotaDefinitionRequest;
import org.cloudfoundry.client.v2.organizationquotadefinitions.ListOrganizationQuotaDefinitionsRequest;
import org.cloudfoundry.client.v2.organizations.DeleteOrganizationRequest;
import org.cloudfoundry.client.v2.organizations.ListOrganizationsRequest;
import org.cloudfoundry.client.v2.privatedomains.DeletePrivateDomainRequest;
import org.cloudfoundry.client.v2.privatedomains.ListPrivateDomainsRequest;
import org.cloudfoundry.client.v2.routes.DeleteRouteRequest;
import org.cloudfoundry.client.v2.routes.ListRoutesRequest;
import org.cloudfoundry.client.v2.routes.RouteEntity;
import org.cloudfoundry.client.v2.securitygroups.DeleteSecurityGroupRequest;
import org.cloudfoundry.client.v2.securitygroups.ListSecurityGroupsRequest;
import org.cloudfoundry.client.v2.serviceinstances.DeleteServiceInstanceRequest;
import org.cloudfoundry.client.v2.serviceinstances.ListServiceInstancesRequest;
import org.cloudfoundry.client.v2.shareddomains.DeleteSharedDomainRequest;
import org.cloudfoundry.client.v2.shareddomains.ListSharedDomainsRequest;
import org.cloudfoundry.client.v2.spacequotadefinitions.DeleteSpaceQuotaDefinitionRequest;
import org.cloudfoundry.client.v2.spacequotadefinitions.ListSpaceQuotaDefinitionsRequest;
import org.cloudfoundry.client.v2.spaces.DeleteSpaceRequest;
import org.cloudfoundry.client.v2.spaces.ListSpacesRequest;
import org.cloudfoundry.client.v2.userprovidedserviceinstances.DeleteUserProvidedServiceInstanceRequest;
import org.cloudfoundry.client.v2.userprovidedserviceinstances.ListUserProvidedServiceInstancesRequest;
import org.cloudfoundry.client.v3.packages.DeletePackageRequest;
import org.cloudfoundry.client.v3.packages.ListPackagesRequest;
import org.cloudfoundry.uaa.UaaClient;
import org.cloudfoundry.uaa.clients.DeleteClientRequest;
import org.cloudfoundry.uaa.clients.ListClientsRequest;
import org.cloudfoundry.uaa.groups.DeleteGroupRequest;
import org.cloudfoundry.uaa.groups.Group;
import org.cloudfoundry.uaa.groups.ListGroupsRequest;
import org.cloudfoundry.uaa.groups.MemberSummary;
import org.cloudfoundry.uaa.identityproviders.DeleteIdentityProviderRequest;
import org.cloudfoundry.uaa.identityproviders.ListIdentityProvidersRequest;
import org.cloudfoundry.uaa.identityproviders.ListIdentityProvidersResponse;
import org.cloudfoundry.uaa.identityzones.DeleteIdentityZoneRequest;
import org.cloudfoundry.uaa.identityzones.ListIdentityZonesRequest;
import org.cloudfoundry.uaa.identityzones.ListIdentityZonesResponse;
import org.cloudfoundry.uaa.users.DeleteUserRequest;
import org.cloudfoundry.uaa.users.ListUsersRequest;
import org.cloudfoundry.util.FluentMap;
import org.cloudfoundry.util.JobUtils;
import org.cloudfoundry.util.PaginationUtils;
import org.cloudfoundry.util.ResourceUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuples;

import javax.net.ssl.SSLException;
import java.time.Duration;
import java.util.Map;

import static org.cloudfoundry.util.tuple.TupleUtils.function;
import static org.cloudfoundry.util.tuple.TupleUtils.predicate;

final class CloudFoundryCleaner {

    private static final Logger LOGGER = LoggerFactory.getLogger("cloudfoundry-client.test");

    private static final Map<String, Boolean> STANDARD_FEATURE_FLAGS = FluentMap.<String, Boolean>builder()
        .entry("app_bits_upload", true)
        .entry("app_scaling", true)
        .entry("diego_docker", true)
        .entry("private_domain_creation", true)
        .entry("route_creation", true)
        .entry("service_instance_creation", true)
        .entry("set_roles_by_username", true)
        .entry("unset_roles_by_username", true)
        .entry("user_org_creation", false)
        .build();

    private final CloudFoundryClient cloudFoundryClient;

    private final NameFactory nameFactory;

    private final UaaClient uaaClient;

    CloudFoundryCleaner(CloudFoundryClient cloudFoundryClient, NameFactory nameFactory, UaaClient uaaClient) {
        this.cloudFoundryClient = cloudFoundryClient;
        this.nameFactory = nameFactory;
        this.uaaClient = uaaClient;
    }

    void clean() {
        Flux.empty()
            .thenMany(cleanBuildpacks(this.cloudFoundryClient, this.nameFactory))
            .thenMany(cleanFeatureFlags(this.cloudFoundryClient))
            .thenMany(cleanRoutes(this.cloudFoundryClient, this.nameFactory))
            .thenMany(cleanUsers(this.cloudFoundryClient, this.nameFactory))
            .thenMany(cleanApplicationsV2(this.cloudFoundryClient, this.nameFactory))
            .thenMany(cleanApplicationsV3(this.cloudFoundryClient, this.nameFactory))
            .thenMany(cleanPackages(this.cloudFoundryClient))
            .thenMany(cleanSecurityGroups(this.cloudFoundryClient, this.nameFactory))
            .thenMany(cleanServiceInstances(this.cloudFoundryClient, this.nameFactory))
            .thenMany(cleanUserProvidedServiceInstances(this.cloudFoundryClient, this.nameFactory))
            .thenMany(cleanSharedDomains(this.cloudFoundryClient, this.nameFactory))
            .thenMany(cleanPrivateDomains(this.cloudFoundryClient, this.nameFactory))
            .thenMany(cleanIdentityProviders(this.uaaClient, this.nameFactory))
            .thenMany(cleanIdentityZones(this.uaaClient, this.nameFactory))
            .thenMany(cleanGroups(this.uaaClient, this.nameFactory))
            .thenMany(cleanUsers(this.uaaClient, this.nameFactory))
            .thenMany(cleanClients(this.uaaClient, this.nameFactory))
            .thenMany(cleanSpaceQuotaDefinitions(this.cloudFoundryClient, this.nameFactory))
            .thenMany(cleanSpaces(this.cloudFoundryClient, this.nameFactory))
            .thenMany(cleanOrganizations(this.cloudFoundryClient, this.nameFactory))
            .thenMany(cleanOrganizationQuotaDefinitions(this.cloudFoundryClient, this.nameFactory))
            .retry(5, t -> t instanceof SSLException)
            .doOnSubscribe(s -> LOGGER.debug(">> CLEANUP <<"))
            .doOnComplete(() -> LOGGER.debug("<< CLEANUP >>"))
            .then()
            .block(Duration.ofMinutes(30));
    }

    private static Flux<Void> cleanApplicationsV2(CloudFoundryClient cloudFoundryClient, NameFactory nameFactory) {
        return PaginationUtils
            .requestClientV2Resources(page -> cloudFoundryClient.applicationsV2()
                .list(ListApplicationsRequest.builder()
                    .page(page)
                    .build()))
            .filter(application -> nameFactory.isApplicationName(ResourceUtils.getEntity(application).getName()))
            .flatMap(application -> removeServiceBindings(cloudFoundryClient, application)
                .thenMany(Flux.just(application)))
            .flatMap(application -> cloudFoundryClient.applicationsV2()
                .delete(DeleteApplicationRequest.builder()
                    .applicationId(ResourceUtils.getId(application))
                    .build())
                .doOnError(t -> LOGGER.error("Unable to delete V2 application {}", ResourceUtils.getEntity(application).getName(), t)));
    }

    private static Flux<Void> cleanApplicationsV3(CloudFoundryClient cloudFoundryClient, NameFactory nameFactory) {
        return PaginationUtils
            .requestClientV3Resources(page -> cloudFoundryClient.applicationsV3()
                .list(org.cloudfoundry.client.v3.applications.ListApplicationsRequest.builder()
                    .page(page)
                    .build()))
            .filter(application -> nameFactory.isApplicationName(application.getName()))
            .flatMap(application -> cloudFoundryClient.applicationsV3()
                .delete(org.cloudfoundry.client.v3.applications.DeleteApplicationRequest.builder()
                    .applicationId(application.getId())
                    .build())
                .doOnError(t -> LOGGER.error("Unable to delete V3 application {}", application.getName(), t)));
    }

    private static Flux<Void> cleanBuildpacks(CloudFoundryClient cloudFoundryClient, NameFactory nameFactory) {
        return PaginationUtils
            .requestClientV2Resources(page -> cloudFoundryClient.buildpacks()
                .list(ListBuildpacksRequest.builder()
                    .page(page)
                    .build()))
            .filter(buildpack -> nameFactory.isBuildpackName(ResourceUtils.getEntity(buildpack).getName()))
            .flatMap(buildpack -> cloudFoundryClient.buildpacks()
                .delete(DeleteBuildpackRequest.builder()
                    .async(true)
                    .buildpackId(ResourceUtils.getId(buildpack))
                    .build())
                .doOnError(t -> LOGGER.error("Unable to delete buildpack {}", ResourceUtils.getEntity(buildpack).getName(), t)))
            .flatMap(job -> JobUtils.waitForCompletion(cloudFoundryClient, job));
    }

    private static Flux<Void> cleanClients(UaaClient uaaClient, NameFactory nameFactory) {
        return PaginationUtils
            .requestUaaResources(startIndex -> uaaClient.clients()
                .list(ListClientsRequest.builder()
                    .startIndex(startIndex)
                    .build()))
            .filter(client -> nameFactory.isClientId(client.getClientId()))
            .flatMap(client -> uaaClient.clients()
                .delete(DeleteClientRequest.builder()
                    .clientId(client.getClientId())
                    .build())
                .doOnError(t -> LOGGER.error("Unable to delete client {}", client.getName(), t))
                .then());
    }

    private static Flux<Void> cleanFeatureFlags(CloudFoundryClient cloudFoundryClient) {
        return cloudFoundryClient.featureFlags()
            .list(ListFeatureFlagsRequest.builder()
                .build())
            .flatMapIterable(ListFeatureFlagsResponse::getFeatureFlags)
            .filter(featureFlag -> STANDARD_FEATURE_FLAGS.containsKey(featureFlag.getName()))
            .filter(featureFlag -> STANDARD_FEATURE_FLAGS.get(featureFlag.getName()) != featureFlag.getEnabled())
            .flatMap(featureFlag -> cloudFoundryClient.featureFlags()
                .set(SetFeatureFlagRequest.builder()
                    .name(featureFlag.getName())
                    .enabled(STANDARD_FEATURE_FLAGS.get(featureFlag.getName()))
                    .build())
                .doOnError(t -> LOGGER.error("Unable to set feature flag {} to {}", featureFlag.getName(), STANDARD_FEATURE_FLAGS.get(featureFlag.getName()), t))
                .then());
    }

    private static Flux<Void> cleanGroups(UaaClient uaaClient, NameFactory nameFactory) {
        return PaginationUtils
            .requestUaaResources(startIndex -> uaaClient.groups()
                .list(ListGroupsRequest.builder()
                    .startIndex(startIndex)
                    .build()))
            .filter(group -> nameFactory.isGroupName(group.getDisplayName()))
            .sort((group1, group2) -> {
                if (containsMember(group1, group2)) {
                    return -1;
                } else if (containsMember(group2, group1)) {
                    return 1;
                } else {
                    return 0;
                }
            })
            .concatMap(group -> uaaClient.groups()
                .delete(DeleteGroupRequest.builder()
                    .groupId(group.getId())
                    .version("*")
                    .build())
                .doOnError(t -> LOGGER.error("Unable to delete group {}", group.getDisplayName(), t))
                .then());
    }

    private static Flux<Void> cleanIdentityProviders(UaaClient uaaClient, NameFactory nameFactory) {
        return uaaClient.identityProviders()
            .list(ListIdentityProvidersRequest.builder()
                .build())
            .flatMapIterable(ListIdentityProvidersResponse::getIdentityProviders)
            .filter(provider -> nameFactory.isIdentityProviderName(provider.getName()))
            .flatMap(provider -> uaaClient.identityProviders()
                .delete(DeleteIdentityProviderRequest.builder()
                    .identityProviderId(provider.getId())
                    .build())
                .doOnError(t -> LOGGER.error("Unable to delete identity provider {}", provider.getName(), t))
                .then());
    }

    private static Flux<Void> cleanIdentityZones(UaaClient uaaClient, NameFactory nameFactory) {
        return uaaClient.identityZones()
            .list(ListIdentityZonesRequest.builder()
                .build())
            .flatMapIterable(ListIdentityZonesResponse::getIdentityZones)
            .filter(zone -> nameFactory.isIdentityZoneName(zone.getName()))
            .flatMap(zone -> uaaClient.identityZones()
                .delete(DeleteIdentityZoneRequest.builder()
                    .identityZoneId(zone.getId())
                    .build())
                .doOnError(t -> LOGGER.error("Unable to delete identity zone {}", zone.getName(), t))
                .then());
    }

    private static Flux<Void> cleanOrganizationQuotaDefinitions(CloudFoundryClient cloudFoundryClient, NameFactory nameFactory) {
        return PaginationUtils
            .requestClientV2Resources(page -> cloudFoundryClient.organizationQuotaDefinitions()
                .list(ListOrganizationQuotaDefinitionsRequest.builder()
                    .page(page)
                    .build()))
            .filter(domain -> nameFactory.isQuotaDefinitionName(ResourceUtils.getEntity(domain).getName()))
            .flatMap(organizationQuotaDefinition -> cloudFoundryClient.organizationQuotaDefinitions()
                .delete(DeleteOrganizationQuotaDefinitionRequest.builder()
                    .async(true)
                    .organizationQuotaDefinitionId(ResourceUtils.getId(organizationQuotaDefinition))
                    .build())
                .flatMap(job -> JobUtils.waitForCompletion(cloudFoundryClient, job))
                .doOnError(t -> LOGGER.error("Unable to delete organization quota definition {}", ResourceUtils.getEntity(organizationQuotaDefinition).getName(), t)));
    }

    private static Flux<Void> cleanOrganizations(CloudFoundryClient cloudFoundryClient, NameFactory nameFactory) {
        return PaginationUtils
            .requestClientV2Resources(page -> cloudFoundryClient.organizations()
                .list(ListOrganizationsRequest.builder()
                    .page(page)
                    .build()))
            .filter(organization -> nameFactory.isOrganizationName(ResourceUtils.getEntity(organization).getName()))
            .flatMap(organization -> cloudFoundryClient.organizations()
                .delete(DeleteOrganizationRequest.builder()
                    .async(true)
                    .organizationId(ResourceUtils.getId(organization))
                    .build())
                .flatMap(job -> JobUtils.waitForCompletion(cloudFoundryClient, job))
                .doOnError(t -> LOGGER.error("Unable to delete organization {}", ResourceUtils.getEntity(organization).getName(), t)));
    }

    private static Flux<Void> cleanPackages(CloudFoundryClient cloudFoundryClient) {
        return PaginationUtils
            .requestClientV3Resources(page -> cloudFoundryClient.packages()
                .list(ListPackagesRequest.builder()
                    .page(page)
                    .build()))
            .filter(p -> true)
            .flatMap(p -> cloudFoundryClient.packages()
                .delete(DeletePackageRequest.builder()
                    .packageId(p.getId())
                    .build())
                .doOnError(t -> LOGGER.error("Unable to delete package", t)));
    }

    private static Flux<Void> cleanPrivateDomains(CloudFoundryClient cloudFoundryClient, NameFactory nameFactory) {
        return PaginationUtils
            .requestClientV2Resources(page -> cloudFoundryClient.privateDomains()
                .list(ListPrivateDomainsRequest.builder()
                    .page(page)
                    .build()))
            .filter(domain -> nameFactory.isDomainName(ResourceUtils.getEntity(domain).getName()))
            .flatMap(privateDomain -> cloudFoundryClient.privateDomains()
                .delete(DeletePrivateDomainRequest.builder()
                    .async(true)
                    .privateDomainId(ResourceUtils.getId(privateDomain))
                    .build())
                .flatMap(job -> JobUtils.waitForCompletion(cloudFoundryClient, job))
                .doOnError(t -> LOGGER.error("Unable to delete private domain {}", ResourceUtils.getEntity(privateDomain).getName(), t)));
    }

    private static Flux<Void> cleanRoutes(CloudFoundryClient cloudFoundryClient, NameFactory nameFactory) {
        return getAllDomains(cloudFoundryClient)
            .flatMap(domains -> PaginationUtils
                .requestClientV2Resources(page -> cloudFoundryClient.routes()
                    .list(ListRoutesRequest.builder()
                        .page(page)
                        .build()))
                .map(resource -> Tuples.of(domains, resource)))
            .filter(predicate((domains, route) -> nameFactory.isDomainName(domains.get(ResourceUtils.getEntity(route).getDomainId())) ||
                nameFactory.isApplicationName(ResourceUtils.getEntity(route).getHost()) ||
                nameFactory.isHostName(ResourceUtils.getEntity(route).getHost())))
            .flatMap(function((domains, route) -> cloudFoundryClient.routes()
                .delete(DeleteRouteRequest.builder()
                    .async(true)
                    .routeId(ResourceUtils.getId(route))
                    .build())
                .flatMap(job -> JobUtils.waitForCompletion(cloudFoundryClient, job))
                .doOnError(t -> {
                    RouteEntity entity = ResourceUtils.getEntity(route);
                    LOGGER.error("Unable to delete route {}.{}:{}{}", entity.getHost(), domains.get(entity.getDomainId()), entity.getPort(), entity.getPath(), t);
                })));
    }

    private static Flux<Void> cleanSecurityGroups(CloudFoundryClient cloudFoundryClient, NameFactory nameFactory) {
        return PaginationUtils
            .requestClientV2Resources(page -> cloudFoundryClient.securityGroups()
                .list(ListSecurityGroupsRequest.builder()
                    .page(page)
                    .build()))
            .filter(securityGroup -> nameFactory.isSecurityGroupName(ResourceUtils.getEntity(securityGroup).getName()))
            .flatMap(securityGroup -> cloudFoundryClient.securityGroups()
                .delete(DeleteSecurityGroupRequest.builder()
                    .securityGroupId(ResourceUtils.getId(securityGroup))
                    .build())
                .doOnError(t -> LOGGER.error("Unable to delete security group {}", ResourceUtils.getEntity(securityGroup).getName(), t))
                .then());
    }

    private static Flux<Void> cleanServiceInstances(CloudFoundryClient cloudFoundryClient, NameFactory nameFactory) {
        return PaginationUtils
            .requestClientV2Resources(page -> cloudFoundryClient.serviceInstances()
                .list(ListServiceInstancesRequest.builder()
                    .page(page)
                    .build()))
            .filter(serviceInstance -> nameFactory.isServiceInstanceName(ResourceUtils.getEntity(serviceInstance).getName()))
            .flatMap(serviceInstance -> cloudFoundryClient.serviceInstances()
                .delete(DeleteServiceInstanceRequest.builder()
                    .async(true)
                    .serviceInstanceId(ResourceUtils.getId(serviceInstance))
                    .build())
                .flatMap(job -> JobUtils.waitForCompletion(cloudFoundryClient, job))
                .doOnError(t -> LOGGER.error("Unable to delete service instance {}", ResourceUtils.getEntity(serviceInstance).getName(), t)));
    }

    private static Flux<Void> cleanSharedDomains(CloudFoundryClient cloudFoundryClient, NameFactory nameFactory) {
        return PaginationUtils.
            requestClientV2Resources(page -> cloudFoundryClient.sharedDomains()
                .list(ListSharedDomainsRequest.builder()
                    .page(page)
                    .build()))
            .filter(domain -> nameFactory.isDomainName(ResourceUtils.getEntity(domain).getName()))
            .flatMap(domain -> cloudFoundryClient.sharedDomains()
                .delete(DeleteSharedDomainRequest.builder()
                    .async(true)
                    .sharedDomainId(ResourceUtils.getId(domain))
                    .build())
                .flatMap(job -> JobUtils.waitForCompletion(cloudFoundryClient, job))
                .doOnError(t -> LOGGER.error("Unable to delete domain {}", ResourceUtils.getEntity(domain).getName(), t)));
    }

    private static Flux<Void> cleanSpaceQuotaDefinitions(CloudFoundryClient cloudFoundryClient, NameFactory nameFactory) {
        return PaginationUtils
            .requestClientV2Resources(page -> cloudFoundryClient.spaceQuotaDefinitions()
                .list(ListSpaceQuotaDefinitionsRequest.builder()
                    .page(page)
                    .build()))
            .filter(quota -> nameFactory.isQuotaDefinitionName(ResourceUtils.getEntity(quota).getName()))
            .flatMap(quota -> cloudFoundryClient.spaceQuotaDefinitions()
                .delete((DeleteSpaceQuotaDefinitionRequest.builder()
                    .async(true)
                    .spaceQuotaDefinitionId(ResourceUtils.getId(quota))
                    .build()))
                .flatMap(job -> JobUtils.waitForCompletion(cloudFoundryClient, job))
                .doOnError(t -> LOGGER.error("Unable to delete space quota definition {}", ResourceUtils.getEntity(quota).getName(), t)));
    }

    private static Flux<Void> cleanSpaces(CloudFoundryClient cloudFoundryClient, NameFactory nameFactory) {
        return PaginationUtils
            .requestClientV2Resources(page -> cloudFoundryClient.spaces()
                .list(ListSpacesRequest.builder()
                    .page(page)
                    .build()))
            .filter(space -> nameFactory.isSpaceName(ResourceUtils.getEntity(space).getName()))
            .flatMap(space -> cloudFoundryClient.spaces()
                .delete(DeleteSpaceRequest.builder()
                    .async(true)
                    .spaceId(ResourceUtils.getId(space))
                    .build())
                .flatMap(job -> JobUtils.waitForCompletion(cloudFoundryClient, job))
                .doOnError(t -> LOGGER.error("Unable to delete space {}", ResourceUtils.getEntity(space).getName(), t)));
    }

    private static Flux<Void> cleanUserProvidedServiceInstances(CloudFoundryClient cloudFoundryClient, NameFactory nameFactory) {
        return PaginationUtils
            .requestClientV2Resources(page -> cloudFoundryClient.userProvidedServiceInstances()
                .list(ListUserProvidedServiceInstancesRequest.builder()
                    .page(page)
                    .build()))
            .filter(userProvidedServiceInstance -> nameFactory.isServiceInstanceName(ResourceUtils.getEntity(userProvidedServiceInstance).getName()))
            .flatMap(userProvidedServiceInstance -> cloudFoundryClient.userProvidedServiceInstances()
                .delete(DeleteUserProvidedServiceInstanceRequest.builder()
                    .userProvidedServiceInstanceId(ResourceUtils.getId(userProvidedServiceInstance))
                    .build())
                .doOnError(t -> LOGGER.error("Unable to delete user provided service instance {}", ResourceUtils.getEntity(userProvidedServiceInstance).getName(), t)));
    }

    private static Flux<Void> cleanUsers(CloudFoundryClient cloudFoundryClient, NameFactory nameFactory) {
        return PaginationUtils
            .requestClientV2Resources(page -> cloudFoundryClient.users()
                .list(org.cloudfoundry.client.v2.users.ListUsersRequest.builder()
                    .page(page)
                    .build()))
            .map(resource -> resource.getMetadata().getId())
            .filter(nameFactory::isUserId)
            .flatMap(userId -> cloudFoundryClient.users()
                .delete(org.cloudfoundry.client.v2.users.DeleteUserRequest.builder()
                    .async(true)
                    .userId(userId)
                    .build())
                .doOnError(t -> LOGGER.error("Unable to delete user {}", userId, t)))
            .flatMap(job -> JobUtils.waitForCompletion(cloudFoundryClient, job));
    }

    private static Flux<Void> cleanUsers(UaaClient uaaClient, NameFactory nameFactory) {
        return PaginationUtils
            .requestUaaResources(startIndex -> uaaClient.users()
                .list(ListUsersRequest.builder()
                    .startIndex(startIndex)
                    .build()))
            .filter(user -> nameFactory.isUserName(user.getUserName()))
            .flatMap(user -> uaaClient.users()
                .delete(DeleteUserRequest.builder()
                    .userId(user.getId())
                    .version("*")
                    .build())
                .doOnError(t -> LOGGER.error("Unable to delete user {}", user.getName(), t))
                .then());
    }

    private static boolean containsMember(Group group, Group candidate) {
        return group.getMembers().stream()
            .map(MemberSummary::getMemberId)
            .anyMatch(id -> candidate.getId().equals(id));
    }

    private static Mono<Map<String, String>> getAllDomains(CloudFoundryClient cloudFoundryClient) {
        return PaginationUtils
            .requestClientV2Resources(page -> cloudFoundryClient.privateDomains()
                .list(ListPrivateDomainsRequest.builder()
                    .page(page)
                    .build()))
            .map(response -> Tuples.of(ResourceUtils.getId(response), ResourceUtils.getEntity(response).getName()))
            .mergeWith(PaginationUtils
                .requestClientV2Resources(page -> cloudFoundryClient.sharedDomains()
                    .list(ListSharedDomainsRequest.builder()
                        .page(page)
                        .build()))
                .map(response -> Tuples.of(ResourceUtils.getId(response), ResourceUtils.getEntity(response).getName())))
            .collectMap(function((id, name) -> id), function((id, name) -> name));
    }

    private static Flux<Void> removeServiceBindings(CloudFoundryClient cloudFoundryClient, ApplicationResource application) {
        return PaginationUtils
            .requestClientV2Resources(page -> cloudFoundryClient.applicationsV2()
                .listServiceBindings(ListApplicationServiceBindingsRequest.builder()
                    .page(page)
                    .applicationId(ResourceUtils.getId(application))
                    .build()))
            .flatMap(serviceBinding -> cloudFoundryClient.applicationsV2()
                .removeServiceBinding(RemoveApplicationServiceBindingRequest.builder()
                    .applicationId(ResourceUtils.getId(application))
                    .serviceBindingId(ResourceUtils.getId(serviceBinding))
                    .build())
                .doOnError(t -> LOGGER.error("Unable to remove service binding from {}", ResourceUtils.getEntity(application).getName(), t)));
    }

}

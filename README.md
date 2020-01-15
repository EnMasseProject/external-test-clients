# external-test-clients
Test clients for applying continuous test load on EnMasse clusters


## Usage

### Prerequisites

* EnMasse installed on a cluster
* Monitoring stack installed on test clients cluster

### Configure EnMasse cluster

1. (Optional) Modify plans

2. Create new namespace `oc new-project amq-upgrade-test`

3. Ensure [tenant-edit](https://github.com/EnMasseProject/enmasse/blob/master/templates/example-roles/020-ClusterRole-tenant-edit.yaml "tenant-edit") example clusterrole or similar is created

4. Create a service account for the test clients to use

```yaml
apiVersion: v1
kind: ServiceAccount
metadata:
  labels:
    app: enmasse
  namespace: <ENMASSE_INFRA_NAMESPACE>
  name: test-clients

```

5. Create a role binding between the above service account and the `tenant-edit` clusterrole

```yaml
apiVersion: rbac.authorization.k8s.io/v1
kind: ClusterRoleBinding
metadata:
  name: "enmasse.io:test-clients"
  labels:
    app: enmasse
roleRef:
  apiGroup: rbac.authorization.k8s.io
  kind: ClusterRole
  name: enmasse.io:tenant-edit
subjects:
- kind: ServiceAccount
  name: test-clients
  namespace: <ENMASSE_INFRA_NAMESPACE>
userNames:
  name: system:serviceaccount:openshift-enmasse:test-clients
```

6. Create an AuthenticationService for the addressspace

``` yaml
apiVersion: admin.enmasse.io/v1beta1
kind: AuthenticationService
metadata:
  name: none-authservice
spec:
  type: none
```

7. Create an addressspace for the test infrastructure

```yaml
apiVersion: enmasse.io/v1beta1
kind: AddressSpace
metadata:
  name: upgrade-test
spec:
  type: standard
  plan: standard-unlimited
  authenticationService:
    name: none-authservice
```

### Configure Test Client cluster
####  Modify Test Clients Configuration

8. Note the following details from the EnMasse cluster
    * Master API URL
    * Token for service account
      * Run `oc get serviceaccount test-clients -n amq-upgrade-test -o yaml`
    * Namespace of test address space
      * Sample is set to `amq-upgrade-test` above
    * Test address space name
      * Sample is set to `upgrade-test` above

9.  Apply the above values to the deployments for the test configurations

    * [API Client deployment](./kubernetes/api-client.deployment.yaml#25 "API Client")
    * [Messaging Client deployment](./kubernetes/messaging-client.deployment.yaml "Messaging Client")
    * [Probe Client deployment](./kubernetes/probe-client.deployment.yaml "Probe Client")


#### Test Client cluster


9. Setup Application monitoring operator which will install prometheus and grafana
    * https://github.com/integr8ly/application-monitoring-operator/

10. Create new namespace `oc new-project amq-upgrade-test`
11. Label namespace `oc label namespace amq-upgrade-test monitoring-key=middleware`
12. Apply the test clients to the clients cluster

```
    oc apply -f ./kubernetes
```
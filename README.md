# external-test-clients
Test clients for applying continuous test load on EnMasse clusters


## Usage

### Prerequisites

* EnMasse installed on a cluster
* Monitoring stack installed on test clients cluster

### Configure EnMasse cluster

1. (Optional) Modify plans

2. Ensure [tenant-edit](https://github.com/EnMasseProject/enmasse/blob/master/templates/example-roles/020-ClusterRole-tenant-edit.yaml "tenant-edit") example clusterrole or similar is created

3. Create a service account for the test clients to use

```yaml
apiVersion: v1
kind: ServiceAccount
metadata:
  labels:
    app: enmasse
  namespace: <ENMASSE_INFRA_NAMESPACE>
  name: test-clients

```

4. Create a role binding between the above service account and the `tenant-edit` clusterrole

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
5. Create an addressspace for the test infrastructure

```yaml
apiVersion: enmasse.io/v1beta1
kind: AddressSpace
metadata:
  name: test-clients
spec:
  type: standard
  plan: standard-unlimited
```

###  Modify Test Clients Configuration

6. Note the following details from the EnMasse cluster
    * Master API URL
    * Token for service account
    * Namespace of test address space
    * Test address space name

7. Apply the above values to the deployments for the test configurations

    * [API Client deployment](./kubernetes/api-client.deployment.yaml#25 "API Client")
    * [Messaging Client deployment](./kubernetes/messaging-client.deployment.yaml "Messaging Client")
    * [Probe Client deployment](./kubernetes/probe-client.deployment.yaml "Probe Client")


### Test Client cluster

8. Apply the test clients to the clients cluster

```
    oc apply -f ./kubernetes
```
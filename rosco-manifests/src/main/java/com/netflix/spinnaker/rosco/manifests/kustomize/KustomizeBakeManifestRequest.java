package com.netflix.spinnaker.rosco.manifests.kustomize;

import com.netflix.spinnaker.rosco.manifests.BakeManifestRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class KustomizeBakeManifestRequest extends BakeManifestRequest {
    String namespace;
}

/*
 * Copyright 2019 Armory, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.rosco.manifests.kustomize.mapping;

import com.fasterxml.jackson.annotation.*;
import lombok.Data;

import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Data
public class Kustomization {

    private List<String> resources = null;

    private List<ConfigMapGenerator> configMapGenerator = null;

    private List<String> cdrs = null;

    private List<String> generators = null;

    private List<Patch> patches = null;

    private List<String> patchesStrategicMerge = null;

    private List<PatchesJson6902> patchesJson6902 = null;

    private String reference;

    @JsonIgnore
    private Map<String, Object> additionalProperties = new HashMap<String, Object>();

    public HashSet<String> getFilesToEvaluate() {
        HashSet<String> toEvaluate = new HashSet<>();
        if (this.resources != null)
            this.resources.forEach(resource -> toEvaluate.add(resource));
        if (this.cdrs != null)
            this.cdrs.forEach(cdr -> toEvaluate.add(cdr));
        if (this.generators != null)
            this.generators.forEach(gen -> toEvaluate.add(gen));
        if (this.patches != null)
            this.patches.forEach(patch -> toEvaluate.add(patch.getPath()));
        if (this.patchesStrategicMerge != null)
            this.patchesStrategicMerge.forEach(patch -> toEvaluate.add(patch));
        if (this.patchesJson6902 != null)
            this.patchesJson6902.forEach(json -> toEvaluate.add(json.getPath()));
        return toEvaluate;
    }

    public Set<String> getFilesToDownload(Path githubPath) {
      return this.configMapGenerator==null? new HashSet<>() :this.configMapGenerator.stream()
        .map(configMapGenerator -> configMapGenerator.getFiles())
        .flatMap(files -> files.stream())
        .map(file -> githubPath.resolve(file).toString())
        .collect(Collectors.toSet());
    }


}

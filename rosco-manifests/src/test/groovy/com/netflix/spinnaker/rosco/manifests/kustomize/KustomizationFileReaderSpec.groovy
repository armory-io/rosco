package com.netflix.spinnaker.rosco.manifests.kustomize

import com.netflix.spinnaker.kork.artifacts.model.Artifact
import com.netflix.spinnaker.rosco.manifests.kustomize.mapping.Kustomization
import com.netflix.spinnaker.rosco.services.ClouddriverService
import retrofit.client.Response
import retrofit.mime.TypedString
import spock.lang.Specification

class KustomizationFileReaderSpec extends Specification {

    def "getKustomization returns a Kustomization object"() {
        given:
        def kustomizationYaml = """
        resources:
        - deployment.yml
        - service.yml
        """
        def clouddriverService = Mock(ClouddriverService)
        def kustomizationFileReader = new KustomizationFileReader(clouddriverService)
        def baseUrl = "https://api.github.com/repos/org/repo/contents/"
        def baseArtifact = Artifact.builder()
            .name("base")
            .reference("https://api.github.com/repos/org/repo/contents/base/")
            .build()

        when:
        Kustomization k = kustomizationFileReader.getKustomization(baseArtifact,"kustomization.yml")

        then:
        1 * clouddriverService.fetchArtifact(_) >> {
            return new Response("test", 200, "", [], new TypedString(kustomizationYaml.trim()))
        }
        k.getResources().sort() == ["deployment.yml", "service.yml"].sort()
        k.getKustomizationFilename() == "kustomization.yml"
    }
}

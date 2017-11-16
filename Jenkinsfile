def pod_label = "exp-inu-alpha${env.BUILD_NUMBER}"
podTemplate(
        label: pod_label,
        containers: [
                containerTemplate(name: 'jnlp', image: env.JNLP_SLAVE_IMAGE, args: '${computer.jnlpmac} ${computer.name}', alwaysPullImage: true),
                containerTemplate(name: 'sbt', image: "${env.PRIVATE_REGISTRY}/library/sbt:2.11-fabric8", ttyEnabled: true, command: 'cat', alwaysPullImage: true),
                containerTemplate(name: 'dind', image: 'docker:stable-dind', privileged: true, ttyEnabled: true, command: 'dockerd', args: '--host=unix:///var/run/docker.sock --host=tcp://0.0.0.0:2375 --storage-driver=vfs')
        ],
        volumes: [
                emptyDirVolume(mountPath: '/var/run', memory: false),
                hostPathVolume(mountPath: "/etc/docker/certs.d/${env.PRIVATE_REGISTRY}/ca.crt", hostPath: "/etc/docker/certs.d/${env.PRIVATE_REGISTRY}/ca.crt"),
                hostPathVolume(mountPath: '/home/jenkins/.kube/config', hostPath: '/etc/kubernetes/admin.conf'),
                persistentVolumeClaim(claimName: env.JENKINS_IVY2, mountPath: '/home/jenkins/.ivy2', readOnly: false),
        ]) {

    node(pod_label) {
        ansiColor('xterm') {
            try {
                stage('git clone') {
                    checkout scm
                }

                container('sbt') {
                    stage('build') {
                        sh 'sbt cluster/compile'
                        sh 'sbt cluster/cpJarsForDocker'
                    }
                }

                def cluster_imgName = "${env.PRIVATE_REGISTRY}/inu/cluster-scala"
                def HEAD = sh(script: 'git rev-parse --short HEAD', returnStdout: true).trim()
                def imgTag = "${HEAD}-${env.BUILD_NUMBER}"
                def cluster_image

                stage('build image') {
                    dir('cluster/target/docker') {
                        cluster_image = build_image(cluster_imgName, imgTag)
                    }
                }

                stage('push image') {
                    push_image(cluster_image)
                }
                // stage('test chart'){
                //     container('helm') {
                //         sh 'helm init --client-only'
                //         dir('test') {
                //             sh 'helm lint .'
                //             def release    = "test-seed${env.BUILD_ID}"
                //             def hostPrefix = "test-seed${env.BUILD_ID}"
                //             def svcName    = "test-seeds${env.BUILD_ID}"
                //             sh "helm install --set=image.repository=${imgName},image.tag=${imgTag},seedHostNamePrefix=${hostPrefix},service.name=${svcName} -n ${release} ."
                //             try {
                //                 sh "helm test ${release} --cleanup"
                //             } catch(err) {
                //                 echo "${error}"
                //                 currentBuild.result = FAILURE
                //             }
                //             finally {
                //                 sh "helm delete --purge ${release}"
                //             }
                //         }
                //     }
                // }

                // stage('package chart') {
                //     container('helm') {
                //         sh 'helm init --client-only'
                //         dir('test') {
                //             echo 'update image tag'
                //             sh """
                //             sed -i \'s/\${BUILD_TAG}/${imgTag}/\' ./templates/NOTES.txt ./values.yaml
                //             """
                //             sh 'helm lint .'
                //             sh 'helm package --destination /var/helm/repo .'
                //         }
                //         dir('/var/helm/repo') {
                //             def flags = "--url ${env.HELM_PUBLIC_REPO_URL}"
                //             flags = fileExists('index.yaml') ? "${flags} --merge index.yaml" : flags
                //             sh "helm repo index ${flags} ."
                //         }
                //     }
                //     build job: 'helm-repository/master', parameters: [string(name: 'commiter', value: "${env.JOB_NAME}\ncommit: ${commit_log}")]
                // }

            } catch (e) {
                echo "${e}"
                currentBuild.result = FAILURE
            }
            finally {

            }
        }
    }
}

def build_image(imgName, imgTag) {
    def mainClass = sh(returnStdout: true, script: 'cat mainClass').trim()
    return docker.build("${imgName}:${imgTag}", "--pull --build-arg JAVA_MAIN_CLASS=${mainClass} .")
}

def push_image(image) {
    docker.withRegistry(env.PRIVATE_REGISTRY_URL, 'docker-login') {
        image.push()
        if( env.BRANCH_NAME == 'master' ){
            image.push('latest')
        }
    }
}
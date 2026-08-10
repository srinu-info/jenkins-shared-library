def call(Map configMap){
    pipeline{
        agent{
            label 'AGENT-1'
        }
        environment{
            appVersion=''
            REGION='us-east-1'
            ACC_ID='597819998113'
            PROJECT='flower-store'
            COMPONENT=configMap.get('component')
            IMAGE_TAG="${env.BUILD_NUMBER}"
        }
        options{
            timeout(time: 30, unit: 'MINUTES')
            disableConcurrentBuilds()
        }
        parameters{
            booleanParam(name: 'deploy', defaultValue: true, description: 'togle this value')
        }
        //Build
        stages{
            stage('Read package.json'){
                steps{
                    script{
                        def packageJson = readJSON file : 'package.json'
                        appVersion= packageJson.version
                        echo "package version : ${appVersion}"
                    }
                }
            }
            stage('Install Dependency'){
                steps{
                    script{
                        sh """
                           npm install
                        """
                    }
                }
            }
            stage('Build Application'){
                steps{
                    script{
                        sh """
                           npm run build
                        """
                    }
                }
            }
            stage('unit testing'){
                steps{
                    script{
                        sh """
                        echo "Unit test cases"
                        """
                    }
                }
            }
            // stage('Sonar scan'){
            //     environment{
            //         scannerHome = tool 'sonar 8.1'
            //     }
            //     steps{
            //         script{
            //         withSonarQubeEnv(installationName: 'sonar 8.1') {
            //         sh "${scannerHome}/bin/sonar-scanner"
            //     }
            //         }
            //     }
            // }
            // stage('Quality gates'){
            //     steps{
            //         timeout(time: 1, unit: "HOURS"){
            //             waitForQualityGate abortPipeline :true
            //         }
            //     }
            // }
            // stage('Trivy Dependency Scan') {
            //     steps {
            //         script {
            //             sh '''
            //             trivy fs \
            //             --severity HIGH,CRITICAL \
            //             --ignore-unfixed \
            //             --exit-code 1 \
            //             .
            //             '''
            //         }
            //     }
            // }
            stage('Docker build'){
                steps{
                    script{
                        withAWS(credentials: 'aws-creds', region: 'us-east-1'){
                            sh """                       
                            docker build -t ${ACC_ID}.dkr.ecr.us-east-1.amazonaws.com/${PROJECT}/${COMPONENT}:${IMAGE_TAG} .
                            """
                        }                    
                    }
                }
            }
            // stage('Trivy Image Scan'){
            //     steps{
            //         script{
            //             sh """
            //             trivy image \
            //             --severity HIGH,CRITICAL \
            //             --ignore-unfixed \
            //             --exit-code 1 \
            //             ${ACC_ID}.dkr.ecr.us-east-1.amazonaws.com/${PROJECT}/${COMPONENT}:${appVersion}
            //             """
            //         }
            //     }
            // }
            stage('Push image to ECR'){
                steps{
                    script{
                        withAWS(credentials: 'aws-creds', region: 'us-east-1'){
                            sh """
                            aws ecr get-login-password --region ${REGION} | docker login --username AWS --password-stdin ${ACC_ID}.dkr.ecr.us-east-1.amazonaws.com
                            docker push ${ACC_ID}.dkr.ecr.us-east-1.amazonaws.com/${PROJECT}/${COMPONENT}:${IMAGE_TAG}
                            """
                        }                    
                    }
                }
            }   
            stage('Trigger Deploy'){
                when {
                    branch 'development'
                }
                steps{
                    script{
                        build job: "STORE/${COMPONENT}-cd-dev",
                        parameters:[
                            string(name: 'IMAGE_TAG', value: "${IMAGE_TAG}"),
                            string(name: 'deploy_to', value: 'dev')
                        ],
                        propagate: false,
                        wait: false                                    
                    }
                }
            }
        }

        post{   
            always{
            cleanWs()
            }       
            success{
                echo ' BUILD SUCCESS'
            }
            failure{
                echo ' BUILD FAILED'
            }
        }
    }
}
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
            booleanParam(name: 'deploy', defaultValue: false, description: 'togle this value')
        }
        //Build
        stages{
            stage('Checkout'){
                steps{
                    checkout scm 
                    //source code management checkout the code using already configured git url and branch
                }
            }
        }
                
            stage('Read pom.xml'){
                steps{
                    script{
                        def pom = readMavenPom()
                        appVersion= pom.version
                        echo "app version : ${appVersion}"
                    }
                }
            }
            stage('Build Application'){
                steps{
                    script{
                        sh """
                        mvn clean package -DskipTests
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
            // // already jenkins admin installed sonarqube in global tool configuration 
            //     }
            //     steps{
            //         script{
            //         withSonarQubeEnv(installationName: 'sonar 8.1') {
            // //withSonarQubeEnv using this jenkins securely injencts sonar url and token configured in configure system
            //         sh "${scannerHome}/bin/sonar-scanner"
            //     }
            //         }
            //     }
            // }
            // stage('Quality gates'){
            //     steps{
            //         timeout(time: 1, unit: "HOURS"){
            //    // if SonarQube server hangs without timeout build never complete with timeout pipeline aborted 
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
                    expression {params.deploy}
                }
                steps{
                    script{
                        build job: "STORE/${COMPONENT}-cd-dev",
                        parameters:[
                            string(name: 'IMAGE_TAG', value: "${IMAGE_TAG}"),
                            string(name: 'deploy_to', value: 'dev')
                        ],
                        propagate: false, //if cd fails ci also failed
                        wait: false     // no need to wait for cd completion                               
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

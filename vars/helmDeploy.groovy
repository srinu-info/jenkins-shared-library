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
            
        }
        options{
            timeout(time: 30, unit: 'MINUTES')
            disableConcurrentBuilds()
        }
        parameters{
            string(name: 'IMAGE_TAG', description: 'Image version of the application')
            choice(name: 'deploy_to', choices: ['dev', 'prod'], description: 'Pick the Environment')
        }
        //Build
        stages{         
            stage('Deploy'){
                steps{
                    script{
                        withAWS(credentials: 'aws-creds', region: 'us-east-1'){
                            sh """
                                aws eks update-kubeconfig --region ${REGION} --name "${PROJECT}-${params.deploy_to}"
                                kubectl get nodes
                                kubectl apply -f namespace.yml
                                cd ${COMPONENT}
                                
                                helm upgrade --install ${COMPONENT} . -f values-${params.deploy_to}.yaml -n ${params.deploy_to}-store --set deployment.imageVersion=${params.IMAGE_TAG}
                                kubectl rollout status deployment/${COMPONENT} -n ${params.deploy_to}-store
                            """
                        }                   
                    }
                }
            }
            stage('check status'){
                steps{
                    script{
                        withAWS(credentials: 'aws-creds', region: 'us-east-1'){
                            def deploymentStatus=sh(returnStdout: true, script:"kubectl rollout status deployment/${COMPONENT} --timeout=180s -n ${params.deploy_to}-store || echo FAILED").trim()
                            if(deploymentStatus.contains("successfully rolled out")){
                                echo "Deployment is success"
                            }else{
                                sh """
                                helm rollback ${COMPONENT} -n ${params.deploy_to}-store
                                """
                                def rollbackStatus=sh(returnStdout:true, script:"kubectl rollout status deployment/${COMPONENT} --timeout=180s -n ${params.deploy_to}-store || echo FAILED").trim()
                            if(rollbackStatus.contains("successfully rolled out")){
                                echo "Deployment is failure,  Rollback success"
                            }else{
                            error "Deployment failure, Rollback failure"
                            }
                        }                   
                    }
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
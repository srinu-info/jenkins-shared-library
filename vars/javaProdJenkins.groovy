def call(Map configMap) {
    pipeline {
        agent {
            label 'AGENT-1'
        }

        environment {
            appVersion = ''
            REGION = 'us-east-1'
            ACC_ID = '597819998113'
            PROJECT = 'flower-store'
            COMPONENT = configMap.get('component')
            IMAGE_TAG = "${env.BUILD_NUMBER}"
        }

        options {
            timeout(time: 30, unit: 'MINUTES')
            disableConcurrentBuilds()
        }

        stages {

            stage('Read pom.xml') {
                steps {
                    script {
                        def pom = readMavenPom file: 'pom.xml'
                        appVersion = pom.version
                        echo "Application Version : ${appVersion}"
                        echo "Image Tag : ${IMAGE_TAG}"
                    }
                }
            }

            stage('Build Application') {
                steps {
                    sh '''
                        mvn clean package -DskipTests
                    '''
                }
            }

            stage('Docker Build') {
                steps {
                    script {
                        withAWS(credentials: 'aws-creds', region: "${REGION}") {

                            sh """
                            docker build \
                            -t ${ACC_ID}.dkr.ecr.${REGION}.amazonaws.com/${PROJECT}/${COMPONENT}:${IMAGE_TAG} .
                            """

                        }
                    }
                }
            }

            stage('Push Image to ECR') {
                steps {
                    script {
                        withAWS(credentials: 'aws-creds', region: "${REGION}") {

                            sh """
                            aws ecr get-login-password --region ${REGION} | \
                            docker login --username AWS --password-stdin \
                            ${ACC_ID}.dkr.ecr.${REGION}.amazonaws.com

                            docker push \
                            ${ACC_ID}.dkr.ecr.${REGION}.amazonaws.com/${PROJECT}/${COMPONENT}:${IMAGE_TAG}
                            """

                        }
                    }
                }
            }

            stage('Release Information') {
                steps {
                    echo "==============================================="
                    echo "Production Image Created Successfully"
                    echo "Component : ${COMPONENT}"
                    echo "Image Tag : ${IMAGE_TAG}"
                    echo ""
                    echo "Deploy using CD Job"
                    echo "IMAGE_TAG = ${IMAGE_TAG}"
                    echo "deploy_to = prod"
                    echo "==============================================="
                }
            }

        }

        post {

            always {
                cleanWs()
            }

            success {
                echo 'PRODUCTION BUILD SUCCESS'
            }

            failure {
                echo 'PRODUCTION BUILD FAILED'
            }
        }
    }
}
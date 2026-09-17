def call(Map config = [:]) {

    pipeline {

        agent any

        stages {

            stage('Clone') {
                steps {
                    checkout scm
                }
            }

            stage('Load Configuration') {
                steps {
                    script {
                        def configFile = config.configFile ?: 'config/deployment.conf'
                        def configContent = readFile(file: configFile)

                        configContent.readLines().each { line ->

                            line = line.trim()

                            if (line && !line.startsWith('#') && line.contains('=')) {

                                def parts = line.split('=', 2)

                                def key = parts[0].trim()
                                def value = parts[1].trim()

                                env."${key}" = value
                            }
                        }

                        echo "Configuration loaded"
                        echo "Environment: ${env.ENVIRONMENT}"
                        echo "Code Base Path: ${env.CODE_BASE_PATH}"
                    }
                }
            }

            stage('SonarQube Analysis') {
                steps {
                    echo "Running SonarQube Analysis..."

                    withSonarQubeEnv('SonarQube') {
                        sh '''
                            mvn clean verify \
                            org.sonarsource.scanner.maven:sonar-maven-plugin:3.10.0.2594:sonar \
                            -Dsonar.projectKey=assignment-6 \
                            -Dsonar.projectName=assignment-6
                        '''
                    }
                }
            }

            stage('Quality Gate') {
                steps {
                    timeout(time: 5, unit: 'MINUTES') {
                        waitForQualityGate abortPipeline: true
                    }
                }
            }

            stage('User Approval') {
                when {
                    expression {
                        return env.KEEP_APPROVAL_STAGE?.toBoolean()
                    }
                }

                steps {
                    input(
                        message: "Deploy to ${env.ENVIRONMENT}?",
                        ok: 'Approve'
                    )
                }
            }

            stage('Playbook Execution') {
                steps {
                    sh """
                        ansible-playbook site.yml \
                        -e environment=${env.ENVIRONMENT} \
                        -e code_base_path=${env.CODE_BASE_PATH}
                    """
                }
            }

            stage('Notification') {
                steps {
                    slackSend(
                        channel: "#${env.SLACK_CHANNEL_NAME}",
                        message: env.ACTION_MESSAGE
                    )
                }
            }

        }
    }
}

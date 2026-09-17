def call(Map config = [:]) {

    def cfg = [:]

    pipeline {

        agent any

        stages {

            stage('Clone') {
                steps {
                    echo "Cloning application repository..."

                    checkout scm

                    echo "Reading configuration file..."

                    def configFile = config.configFile ?: 'config/deployment.conf'
                    def configContent = readFile file: configFile

                    configContent.readLines().each { line ->

                        line = line.trim()

                        if (line && !line.startsWith('#') && line.contains('=')) {

                            def parts = line.split('=', 2)

                            def key = parts[0].trim()
                            def value = parts[1].trim()

                            cfg[key] = value
                        }
                    }

                    echo "Configuration loaded successfully"
                    echo "Environment: ${cfg['ENVIRONMENT']}"
                    echo "Code Base Path: ${cfg['CODE_BASE_PATH']}"
                }
            }

            stage('SonarQube Analysis') {
                steps {
                    echo "Running SonarQube Analysis..."

                    withSonarQubeEnv('SonarQube') {
                        sh 'mvn clean verify sonar:sonar'
                    }
                }
            }

            stage('Quality Gate') {
                steps {
                    echo "Checking SonarQube Quality Gate..."

                    timeout(time: 5, unit: 'MINUTES') {
                        waitForQualityGate abortPipeline: true
                    }
                }
            }

            stage('User Approval') {
                when {
                    expression {
                        return cfg['KEEP_APPROVAL_STAGE']?.toBoolean()
                    }
                }

                steps {
                    input message: "Deploy to ${cfg['ENVIRONMENT']}?",
                          ok: 'Approve'
                }
            }

            stage('Playbook Execution') {
                steps {
                    echo "Executing Ansible Playbook..."

                    sh """
                        ansible-playbook site.yml \
                        -e environment=${cfg['ENVIRONMENT']} \
                        -e code_base_path=${cfg['CODE_BASE_PATH']}
                    """
                }
            }

            stage('Notification') {
                steps {
                    echo "Sending notification..."

                    echo "Slack Channel: ${cfg['SLACK_CHANNEL_NAME']}"
                    echo "Message: ${cfg['ACTION_MESSAGE']}"
                }
            }
        }
    }
}

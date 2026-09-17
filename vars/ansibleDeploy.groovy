def call(Map config = [:]) {

    pipeline {

        agent any

        stages {

            stage('Clone') {
                steps {
                    echo "Cloning source code..."
                    checkout scm
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
                        return config.keepApprovalStage == true
                    }
                }

                steps {
                    input message: 'Do you want to continue deployment?',
                          ok: 'Approve'
                }
            }

            stage('Playbook Execution') {
                steps {
                    echo "Executing Ansible Playbook..."

                    sh """
                        ansible-playbook ${config.playbook ?: 'site.yml'} \
                        -e environment=${config.environment ?: 'dev'} \
                        -e code_base_path=${config.codeBasePath ?: 'env/dev'}
                    """
                }
            }

            stage('Notification') {
                steps {
                    echo "Sending notification..."

                    echo "Channel: ${config.slackChannelName}"
                    echo "Message: ${config.actionMessage}"
                }
            }
        }
    }
}

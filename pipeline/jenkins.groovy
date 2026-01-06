pipeline {
    agent {
        kubernetes {
            yaml '''
apiVersion: v1
kind: Pod
spec:
  containers:
  - name: go-tools
    image: golang:1.23-alpine
    command:
    - cat
    tty: true
    resources:
      limits:
        memory: "1024Mi"
        cpu: "512m"
'''
        }
    }

    parameters {
        choice(name: 'OS', choices: ['linux', 'darwin', 'windows'], description: 'Target operating system')
        choice(name: 'ARCH', choices: ['amd64', 'arm64'], description: 'Target architecture')
        booleanParam(name: 'SKIP_TESTS', defaultValue: false, description: 'Skip running tests')
        booleanParam(name: 'SKIP_LINT', defaultValue: false, description: 'Skip running linter')
    }

    environment {
        // Отримуємо версію з Git всередині контейнера
        APP_VERSION = "" 
    }

    stages {
        stage('Prepare') {
            steps {
                container('go-tools') {
                    // Встановлюємо необхідні інструменти, яких немає в образі alpine
                    sh "apk add --no-cache make git"
                    script {
                        env.APP_VERSION = sh(script: "git describe --tags --abbrev=0 2>/dev/null || echo 'v0.0.1'", returnStdout: true).trim()
                        env.COMMIT_HASH = sh(script: "git rev-parse --short HEAD", returnStdout: true).trim()
                        env.VERSION = "${env.APP_VERSION}-${env.COMMIT_HASH}"
                    }
                }
            }
        }

        stage('Lint') {
            when { expression { return !params.SKIP_LINT } }
            steps {
                container('go-tools') {
                    echo "Running Linter..."
                    sh "make lint"
                }
            }
        }

        stage('Test') {
            when { expression { return !params.SKIP_TESTS } }
            steps {
                container('go-tools') {
                    echo "Running Tests..."
                    sh "make test"
                }
            }
        }

        stage('Build') {
            steps {
                container('go-tools') {
                    echo "Building kbot for ${params.OS}/${params.ARCH}..."
                    sh "make build TARGETOS=${params.OS} TARGETARCH=${params.ARCH} VERSION=${env.VERSION}"
                }
            }
        }

        stage('Artifact') {
            steps {
                echo "Archiving build artifact..."
                archiveArtifacts artifacts: 'kbot', fingerprint: true
            }
        }
    }

    post {
        always {
            container('go-tools') {
                echo "Cleaning up..."
                sh "make clean || true"
            }
        }
        success {
            echo "Build successful: ${env.VERSION}"
        }
        failure {
            echo "Build failed. Check the logs."
        }
    }
}
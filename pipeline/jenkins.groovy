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
        VERSION = ""
    }

    stages {
        stage('Prepare') {
            steps {
                container('go-tools') {
                    // КРОК 1: Встановлюємо git, щоб команда git config стала доступною
                    echo "Installing build tools..."
                    sh "apk add --no-cache make git"
                    
                    // КРОК 2: Налаштовуємо права доступу (тепер git встановлено!)
                    sh "git config --global --add safe.directory ${WORKSPACE}"
                    
                    script {
                        def app_version = sh(script: "git describe --tags --abbrev=0 2>/dev/null || echo 'v0.0.1'", returnStdout: true).trim()
                        def commit_hash = sh(script: "git rev-parse --short HEAD", returnStdout: true).trim()
                        env.VERSION = "${app_version}-${commit_hash}"
                        echo "Target Version for build: ${env.VERSION}"
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
                // Зберігаємо файл, який створила команда make build
                archiveArtifacts artifacts: 'kbot', fingerprint: true
            }
        }
    }

    post {
        always {
            container('go-tools') {
                echo "Cleaning up..."
                // Додаємо перевірку на випадок, якщо Prepare не встиг встановити git
                sh "apk add --no-cache make git || true"
                sh "git config --global --add safe.directory ${WORKSPACE} || true"
                sh "make clean || true"
            }
        }
        success {
            echo "Build finished successfully!"
        }
        failure {
            echo "Build failed. Check the stage logs."
        }
    }
}
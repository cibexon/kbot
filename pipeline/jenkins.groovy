pipeline {
    agent {
        kubernetes {
            yaml '''
apiVersion: v1
kind: Pod
spec:
  containers:
  - name: go-tools
    image: golang:1.24-alpine
    command:
    - cat
    tty: true
    resources:
      limits:
        memory: "1536Mi"
        cpu: "1000m"
'''
        }
    }

    parameters {
        choice(name: 'OS', choices: ['linux', 'darwin', 'windows'], description: 'Target operating system')
        choice(name: 'ARCH', choices: ['amd64', 'arm64'], description: 'Target architecture')
        booleanParam(name: 'SKIP_TESTS', defaultValue: false, description: 'Skip running tests')
        booleanParam(name: 'SKIP_LINT', defaultValue: false, description: 'Skip running linter')
    }

    stages {
        stage('Prepare') {
            steps {
                container('go-tools') {
                    sh 'apk add --no-cache make git'
                    sh "git config --global --add safe.directory ${WORKSPACE}"
                    
                    script {
                        def app_v = sh(script: "git describe --tags --abbrev=0 2>/dev/null || echo 'v0.0.1'", returnStdout: true).trim()
                        def commit = sh(script: "git rev-parse --short HEAD", returnStdout: true).trim()
                        env.VERSION = "${app_v}-${commit}"
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
                    // Використовуємо одинарні лапки, щоб Groovy не чіпав $PATH та $(...)
                    sh 'export PATH=$PATH:$(go env GOPATH)/bin && make lint'
                }
            }
        }

        stage('Test') {
            when { expression { return !params.SKIP_TESTS } }
            steps {
                container('go-tools') {
                    echo "Running Tests..."
                    sh 'make test'
                }
            }
        }

        stage('Build') {
            steps {
                container('go-tools') {
                    echo "Building kbot for ${params.OS}/${params.ARCH}..."
                    // Тут використовуємо подвійні лапки, бо нам потрібні змінні params та env від Jenkins
                    sh "make build TARGETOS=${params.OS} TARGETARCH=${params.ARCH} VERSION=${env.VERSION}"
                }
            }
        }

        stage('Artifact') {
            steps {
                echo "Archiving build artifact..."
                archiveArtifacts artifacts: 'kbot', fingerprint: true, allowEmptyArchive: true
            }
        }
    }

    post {
        always {
            container('go-tools') {
                echo "Cleaning up..."
                sh 'apk add --no-cache make git || true'
                sh "git config --global --add safe.directory ${WORKSPACE} || true"
                sh 'make clean || true'
            }
        }
    }
}
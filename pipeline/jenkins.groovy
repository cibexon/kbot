pipeline {
    agent any

    parameters {
        choice(
            name: 'OS',
            choices: ['linux', 'darwin', 'windows'],
            description: 'Target operating system'
        )
        choice(
            name: 'ARCH',
            choices: ['amd64', 'arm64'],
            description: 'Target architecture'
        )
        booleanParam(
            name: 'SKIP_TESTS',
            defaultValue: false,
            description: 'Skip running tests'
        )
        booleanParam(
            name: 'SKIP_LINT',
            defaultValue: false,
            description: 'Skip running linter'
        )
    }

    environment {
        // Визначаємо версію так само, як у Makefile
        APP_VERSION = sh(script: "git describe --tags --abbrev=0 2>/dev/null || echo 'v0.0.1' | tr -d '\\n'", returnStdout: true)
        COMMIT_HASH = sh(script: "git rev-parse --short HEAD | tr -d '\\n'", returnStdout: true)
        VERSION = "${APP_VERSION}-${COMMIT_HASH}"
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Lint') {
            when { expression { return !params.SKIP_LINT } }
            steps {
                echo "Running Linter..."
                // Викликаємо make lint
                sh "make lint"
            }
        }

        stage('Test') {
            when { expression { return !params.SKIP_TESTS } }
            steps {
                echo "Running Tests..."
                // Викликаємо make test
                sh "make test"
            }
        }

        stage('Build') {
            steps {
                echo "Building kbot for ${params.OS}/${params.ARCH}..."
                // Використовуємо параметри з інтерфейсу Jenkins
                sh "make build TARGETOS=${params.OS} TARGETARCH=${params.ARCH} VERSION=${env.VERSION}"
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
            echo "Cleaning up..."
            sh "make clean"
        }
        success {
            echo "Build successful for ${env.VERSION}!"
        }
        failure {
            echo "Build failed. Check the logs."
        }
    }
}
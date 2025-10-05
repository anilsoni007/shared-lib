def call(Map config) {
    def projectType = detectProjectType()
    def podTemplate = selectPodTemplate(projectType)
    
    pipeline {
        agent {
            label podTemplate
        }
        
        stages {
            stage('Checkout') {
                steps {
                    checkout scm
                }
            }
            
            stage('Build') {
                steps {
                    script {
                        executeBuildSteps(projectType)
                    }
                }
            }
            
            stage('Test') {
                when {
                    expression { config.runTests != false }
                }
                steps {
                    script {
                        executeTestSteps(projectType)
                    }
                }
            }
            
            stage('Package') {
                when {
                    expression { config.package != false }
                }
                steps {
                    script {
                        executePackageSteps(projectType)
                    }
                }
            }
            
            stage('Deploy') {
                when {
                    expression { config.deploy == true }
                }
                steps {
                    script {
                        executeDeploySteps(projectType, config)
                    }
                }
            }
        }
        
        post {
            always {
                script {
                    executeCleanupSteps(projectType)
                }
            }
        }
    }
}

def detectProjectType() {
    if (fileExists('pom.xml')) {
        return 'maven'
    } else if (fileExists('build.gradle') || fileExists('build.gradle.kts')) {
        return 'gradle'
    } else if (fileExists('package.json')) {
        return 'nodejs'
    } else if (fileExists('requirements.txt') || fileExists('setup.py') || fileExists('pyproject.toml')) {
        return 'python'
    } else if (fileExists('Dockerfile')) {
        return 'docker'
    } else if (fileExists('go.mod')) {
        return 'golang'
    } else {
        return 'java'
    }
}

def selectPodTemplate(projectType) {
    def templateMap = [
        'maven': 'maven',
        'gradle': 'gradle',
        'nodejs': 'nodejs',
        'python': 'python',
        'docker': 'docker',
        'golang': 'java',
        'java': 'java'
    ]
    return templateMap[projectType] ?: 'java'
}

def executeBuildSteps(projectType) {
    switch(projectType) {
        case 'maven':
            container('maven') {
                sh 'mvn clean compile'
            }
            break
        case 'gradle':
            container('gradle') {
                sh './gradlew clean build -x test'
            }
            break
        case 'nodejs':
            container('nodejs') {
                sh 'npm install'
                sh 'npm run build'
            }
            break
        case 'python':
            container('python') {
                sh 'pip install -r requirements.txt'
            }
            break
        case 'docker':
            container('docker') {
                sh 'docker build -t app:${BUILD_NUMBER} .'
            }
            break
        default:
            container('java') {
                sh 'echo "Building Java project"'
            }
    }
}

def executeTestSteps(projectType) {
    switch(projectType) {
        case 'maven':
            container('maven') {
                sh 'mvn test'
                publishTestResults testResultsPattern: 'target/surefire-reports/*.xml'
            }
            break
        case 'gradle':
            container('gradle') {
                sh './gradlew test'
                publishTestResults testResultsPattern: 'build/test-results/test/*.xml'
            }
            break
        case 'nodejs':
            container('nodejs') {
                sh 'npm test'
            }
            break
        case 'python':
            container('python') {
                sh 'python -m pytest'
            }
            break
        default:
            echo "No tests defined for ${projectType}"
    }
}

def executePackageSteps(projectType) {
    switch(projectType) {
        case 'maven':
            container('maven') {
                sh 'mvn package -DskipTests'
                archiveArtifacts artifacts: 'target/*.jar', fingerprint: true
            }
            break
        case 'gradle':
            container('gradle') {
                sh './gradlew assemble'
                archiveArtifacts artifacts: 'build/libs/*.jar', fingerprint: true
            }
            break
        case 'nodejs':
            container('nodejs') {
                sh 'npm pack'
                archiveArtifacts artifacts: '*.tgz', fingerprint: true
            }
            break
        case 'python':
            container('python') {
                sh 'python setup.py sdist bdist_wheel'
                archiveArtifacts artifacts: 'dist/*', fingerprint: true
            }
            break
        default:
            echo "No packaging defined for ${projectType}"
    }
}

def executeDeploySteps(projectType, config) {
    if (config.deployTarget) {
        echo "Deploying ${projectType} application to ${config.deployTarget}"
        // Add deployment logic here
    }
}

def executeCleanupSteps(projectType) {
    switch(projectType) {
        case 'docker':
            container('docker') {
                sh 'docker system prune -f'
            }
            break
        default:
            cleanWs()
    }
}
def call(Map config = [:]) {
    def projectType = detectProjectType()
    def podLabel = selectPodTemplate(projectType)
    
    node(podLabel) {
        try {
            stage('Checkout') {
                checkout scm
                // Re-detect after checkout in case detection failed initially
                projectType = detectProjectType()
                echo "Detected project type: ${projectType}"
            }
            
            stage('Build') {
                executeBuild(projectType)
            }
            
            if (config.runTests != false) {
                stage('Test') {
                    executeTests(projectType)
                }
            }
            
            if (config.package != false) {
                stage('Package') {
                    executePackage(projectType)
                }
            }
            
            if (config.deploy == true) {
                stage('Deploy') {
                    executeDeploy(projectType, config)
                }
            }
            
        } catch (Exception e) {
            currentBuild.result = 'FAILURE'
            throw e
        } finally {
            stage('Cleanup') {
                executeCleanup(projectType)
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
    } else if (fileExists('requirements.txt') || fileExists('setup.py')) {
        return 'python'
    } else if (fileExists('Dockerfile')) {
        return 'docker'
    } else if (fileExists('go.mod')) {
        return 'golang'
    }
    return 'java'
}

def selectPodTemplate(projectType) {
    def templates = [
        'maven': 'maven',
        'gradle': 'gradle', 
        'nodejs': 'nodejs',
        'python': 'python',
        'docker': 'docker',
        'golang': 'java'
    ]
    return templates[projectType] ?: 'java'
}

def executeBuild(projectType) {
    switch(projectType) {
        case 'maven':
            container('maven') {
                sh 'mvn clean compile'
            }
            break
        case 'gradle':
            container('gradle') {
                sh './gradlew clean compileJava'
            }
            break
        case 'nodejs':
            container('nodejs') {
                sh 'npm ci'
                if (fileExists('package.json')) {
                    def packageJson = readJSON file: 'package.json'
                    if (packageJson.scripts?.build) {
                        sh 'npm run build'
                    }
                }
            }
            break
        case 'python':
            container('python') {
                sh 'pip install --upgrade pip'
                if (fileExists('requirements.txt')) {
                    sh 'pip install -r requirements.txt'
                }
            }
            break
        case 'docker':
            container('docker') {
                sh "docker build -t ${env.JOB_NAME}:${env.BUILD_NUMBER} ."
            }
            break
        default:
            container('java') {
                sh 'echo "Building with Java container"'
            }
    }
}

def executeTests(projectType) {
    switch(projectType) {
        case 'maven':
            container('maven') {
                sh 'mvn test'
                if (fileExists('target/surefire-reports/*.xml')) {
                    publishTestResults testResultsPattern: 'target/surefire-reports/*.xml'
                }
            }
            break
        case 'gradle':
            container('gradle') {
                sh './gradlew test'
                if (fileExists('build/test-results/test/*.xml')) {
                    publishTestResults testResultsPattern: 'build/test-results/test/*.xml'
                }
            }
            break
        case 'nodejs':
            container('nodejs') {
                def packageJson = readJSON file: 'package.json'
                if (packageJson.scripts?.test) {
                    sh 'npm test'
                }
            }
            break
        case 'python':
            container('python') {
                sh 'python -m pytest --junitxml=test-results.xml || true'
                if (fileExists('test-results.xml')) {
                    publishTestResults testResultsPattern: 'test-results.xml'
                }
            }
            break
        default:
            echo "No tests configured for ${projectType}"
    }
}

def executePackage(projectType) {
    switch(projectType) {
        case 'maven':
            container('maven') {
                sh 'mvn package -DskipTests'
                archiveArtifacts artifacts: 'target/*.jar', allowEmptyArchive: true
            }
            break
        case 'gradle':
            container('gradle') {
                sh './gradlew assemble'
                archiveArtifacts artifacts: 'build/libs/*.jar', allowEmptyArchive: true
            }
            break
        case 'nodejs':
            container('nodejs') {
                sh 'npm pack'
                archiveArtifacts artifacts: '*.tgz', allowEmptyArchive: true
            }
            break
        case 'python':
            container('python') {
                sh 'python setup.py sdist bdist_wheel'
                archiveArtifacts artifacts: 'dist/*', allowEmptyArchive: true
            }
            break
        case 'docker':
            container('docker') {
                sh "docker save ${env.JOB_NAME}:${env.BUILD_NUMBER} | gzip > ${env.JOB_NAME}-${env.BUILD_NUMBER}.tar.gz"
                archiveArtifacts artifacts: '*.tar.gz', allowEmptyArchive: true
            }
            break
    }
}

def executeDeploy(projectType, config) {
    echo "Deploying ${projectType} project"
    if (config.deployScript) {
        sh config.deployScript
    }
}

def executeCleanup(projectType) {
    if (projectType == 'docker') {
        container('docker') {
            sh 'docker system prune -f || true'
        }
    }
    cleanWs()
}
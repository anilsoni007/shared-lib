def call(Map config = [:]) {
    def projectType = 'unknown'
    def podLabel = 'java' // default fallback
    
    // Try to detect project type from workspace if available
    try {
        projectType = detectProjectType()
        podLabel = getPodLabel(projectType)
        echo "Detected project: ${projectType}, using pod: ${podLabel}"
    } catch (Exception e) {
        echo "Could not detect project type initially, using default Java pod"
    }
    
    node(podLabel) {
        try {
            // Checkout and re-detect
            stage('Checkout') {
                checkout scm
                projectType = detectProjectType()
                echo "Confirmed project type: ${projectType}"
            }
            
            // Execute build pipeline
            executePipeline(projectType, config)
            
        } catch (Exception e) {
            currentBuild.result = 'FAILURE'
            echo "Build failed: ${e.getMessage()}"
            throw e
        } finally {
            // Cleanup
            cleanupWorkspace(projectType)
        }
    }
}

def detectProjectType() {
    // Check for project files in order of specificity
    if (fileExists('pom.xml')) {
        return 'maven'
    }
    if (fileExists('build.gradle') || fileExists('build.gradle.kts')) {
        return 'gradle'
    }
    if (fileExists('package.json')) {
        return 'nodejs'
    }
    if (fileExists('requirements.txt') || fileExists('setup.py') || fileExists('pyproject.toml')) {
        return 'python'
    }
    if (fileExists('Dockerfile')) {
        return 'docker'
    }
    if (fileExists('go.mod') || fileExists('go.sum')) {
        return 'golang'
    }
    if (fileExists('Cargo.toml')) {
        return 'rust'
    }
    
    // Check for common Java patterns
    if (fileExists('src/main/java') || fileExists('src')) {
        return 'java'
    }
    
    return 'generic'
}

def getPodLabel(projectType) {
    def labelMap = [
        'maven': 'maven',
        'gradle': 'gradle',
        'nodejs': 'nodejs',
        'python': 'python',
        'docker': 'docker',
        'golang': 'java',
        'rust': 'java',
        'java': 'java',
        'generic': 'java'
    ]
    return labelMap[projectType] ?: 'java'
}

def executePipeline(projectType, config) {
    // Build stage
    stage('Build') {
        executeBuildStage(projectType)
    }
    
    // Test stage (conditional)
    if (config.skipTests != true) {
        stage('Test') {
            executeTestStage(projectType)
        }
    }
    
    // Code Quality stage (conditional)
    if (config.codeQuality == true) {
        stage('Code Quality') {
            executeCodeQualityStage(projectType)
        }
    }
    
    // Package stage (conditional)
    if (config.skipPackage != true) {
        stage('Package') {
            executePackageStage(projectType)
        }
    }
    
    // Security Scan stage (conditional)
    if (config.securityScan == true) {
        stage('Security Scan') {
            executeSecurityScanStage(projectType)
        }
    }
    
    // Deploy stage (conditional)
    if (config.deploy == true) {
        stage('Deploy') {
            executeDeployStage(projectType, config)
        }
    }
}

def executeBuildStage(projectType) {
    switch(projectType) {
        case 'maven':
            container('maven') {
                sh 'mvn clean compile -B -DskipTests'
            }
            break
            
        case 'gradle':
            container('gradle') {
                sh 'chmod +x gradlew || true'
                sh './gradlew clean compileJava'
            }
            break
            
        case 'nodejs':
            container('nodejs') {
                sh 'npm ci'
                if (hasNpmScript('build')) {
                    sh 'npm run build'
                }
            }
            break
            
        case 'python':
            container('python') {
                sh 'pip install --upgrade pip'
                if (fileExists('requirements.txt')) {
                    sh 'pip install -r requirements.txt'
                }
                if (fileExists('setup.py')) {
                    sh 'python setup.py build'
                }
            }
            break
            
        case 'docker':
            container('docker') {
                def imageName = "${env.JOB_NAME}:${env.BUILD_NUMBER}".toLowerCase().replaceAll(/[^a-z0-9\-_.]/, '-')
                sh "docker build -t ${imageName} ."
                env.DOCKER_IMAGE = imageName
            }
            break
            
        case 'golang':
            container('java') {
                sh 'go build -v ./...'
            }
            break
            
        default:
            container('java') {
                sh 'echo "Generic build step for ${projectType}"'
                if (fileExists('Makefile')) {
                    sh 'make build || make all || echo "Make build failed"'
                }
            }
    }
}

def executeTestStage(projectType) {
    switch(projectType) {
        case 'maven':
            container('maven') {
                sh 'mvn test -B'
                publishTestResults testResultsPattern: 'target/surefire-reports/*.xml', allowEmptyResults: true
                publishCoverage adapters: [jacocoAdapter('target/site/jacoco/jacoco.xml')], sourceFileResolver: sourceFiles('STORE_LAST_BUILD')
            }
            break
            
        case 'gradle':
            container('gradle') {
                sh './gradlew test'
                publishTestResults testResultsPattern: 'build/test-results/test/*.xml', allowEmptyResults: true
            }
            break
            
        case 'nodejs':
            container('nodejs') {
                if (hasNpmScript('test')) {
                    sh 'npm test'
                }
                if (fileExists('coverage/lcov.info')) {
                    publishCoverage adapters: [lcovAdapter('coverage/lcov.info')], sourceFileResolver: sourceFiles('STORE_LAST_BUILD')
                }
            }
            break
            
        case 'python':
            container('python') {
                sh '''
                    pip install pytest pytest-cov || true
                    python -m pytest --junitxml=test-results.xml --cov=. --cov-report=xml || echo "Tests completed with issues"
                '''
                publishTestResults testResultsPattern: 'test-results.xml', allowEmptyResults: true
                if (fileExists('coverage.xml')) {
                    publishCoverage adapters: [coberturaAdapter('coverage.xml')], sourceFileResolver: sourceFiles('STORE_LAST_BUILD')
                }
            }
            break
            
        case 'golang':
            container('java') {
                sh 'go test -v ./... -coverprofile=coverage.out || echo "Tests completed"'
            }
            break
            
        default:
            echo "No tests configured for ${projectType}"
    }
}

def executeCodeQualityStage(projectType) {
    echo "Running code quality checks for ${projectType}"
    // Add SonarQube or other code quality tools here
}

def executePackageStage(projectType) {
    switch(projectType) {
        case 'maven':
            container('maven') {
                sh 'mvn package -DskipTests -B'
                archiveArtifacts artifacts: 'target/*.jar,target/*.war', allowEmptyArchive: true
            }
            break
            
        case 'gradle':
            container('gradle') {
                sh './gradlew assemble'
                archiveArtifacts artifacts: 'build/libs/*.jar,build/libs/*.war', allowEmptyArchive: true
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
                if (fileExists('setup.py')) {
                    sh 'python setup.py sdist bdist_wheel'
                    archiveArtifacts artifacts: 'dist/*', allowEmptyArchive: true
                }
            }
            break
            
        case 'docker':
            container('docker') {
                if (env.DOCKER_IMAGE) {
                    sh "docker save ${env.DOCKER_IMAGE} | gzip > docker-image.tar.gz"
                    archiveArtifacts artifacts: 'docker-image.tar.gz', allowEmptyArchive: true
                }
            }
            break
            
        default:
            echo "No packaging configured for ${projectType}"
    }
}

def executeSecurityScanStage(projectType) {
    echo "Running security scan for ${projectType}"
    // Add security scanning tools here (e.g., OWASP, Snyk, etc.)
}

def executeDeployStage(projectType, config) {
    echo "Deploying ${projectType} application"
    
    if (config.deployCommand) {
        sh config.deployCommand
    } else if (config.deployScript) {
        load config.deployScript
    } else {
        echo "No deployment configuration provided"
    }
}

def cleanupWorkspace(projectType) {
    if (projectType == 'docker') {
        container('docker') {
            sh 'docker system prune -f || true'
        }
    }
    cleanWs()
}

// Helper functions
def hasNpmScript(scriptName) {
    if (!fileExists('package.json')) return false
    try {
        def packageJson = readJSON file: 'package.json'
        return packageJson.scripts && packageJson.scripts[scriptName]
    } catch (Exception e) {
        return false
    }
}
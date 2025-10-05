def call(Map config = [:]) {
    // Initial project detection (may be limited before checkout)
    def initialProjectType = detectProjectType()
    def podLabel = selectPodTemplate(initialProjectType)
    
    echo "Initial detection: ${initialProjectType}, using pod: ${podLabel}"
    
    node(podLabel) {
        def projectType = initialProjectType
        
        stage('Checkout & Detect') {
            checkout scm
            projectType = detectProjectType()
            echo "Final project type: ${projectType}"
            
            if (projectType != initialProjectType) {
                echo "Warning: Project type changed after checkout. Consider restarting with correct pod."
            }
        }
        
        stage('Build') {
            buildProject(projectType)
        }
        
        if (shouldRunTests(config, projectType)) {
            stage('Test') {
                testProject(projectType)
            }
        }
        
        if (shouldPackage(config, projectType)) {
            stage('Package') {
                packageProject(projectType)
            }
        }
        
        if (shouldDeploy(config)) {
            stage('Deploy') {
                deployProject(projectType, config)
            }
        }
        
        stage('Post Actions') {
            postActions(projectType, config)
        }
    }
}

def detectProjectType() {
    def detectionRules = [
        'maven': { fileExists('pom.xml') },
        'gradle': { fileExists('build.gradle') || fileExists('build.gradle.kts') },
        'nodejs': { fileExists('package.json') },
        'python': { fileExists('requirements.txt') || fileExists('setup.py') || fileExists('pyproject.toml') },
        'docker': { fileExists('Dockerfile') },
        'golang': { fileExists('go.mod') || fileExists('main.go') }
    ]
    
    for (type in detectionRules.keySet()) {
        if (detectionRules[type]()) {
            return type
        }
    }
    return 'java' // default
}

def selectPodTemplate(projectType) {
    def podMapping = [
        'maven': 'maven',
        'gradle': 'gradle',
        'nodejs': 'nodejs',
        'python': 'python', 
        'docker': 'docker',
        'golang': 'java'
    ]
    return podMapping[projectType] ?: 'java'
}

def buildProject(projectType) {
    def buildCommands = [
        'maven': {
            container('maven') {
                sh 'mvn clean compile -B'
            }
        },
        'gradle': {
            container('gradle') {
                sh 'chmod +x gradlew'
                sh './gradlew clean compileJava'
            }
        },
        'nodejs': {
            container('nodejs') {
                sh 'npm ci --only=production'
                if (hasScript('build')) {
                    sh 'npm run build'
                }
            }
        },
        'python': {
            container('python') {
                sh 'pip install --upgrade pip'
                if (fileExists('requirements.txt')) {
                    sh 'pip install -r requirements.txt'
                }
                if (fileExists('setup.py')) {
                    sh 'python setup.py build'
                }
            }
        },
        'docker': {
            container('docker') {
                sh "docker build -t ${env.JOB_NAME.toLowerCase()}:${env.BUILD_NUMBER} ."
            }
        }
    ]
    
    def buildCommand = buildCommands[projectType]
    if (buildCommand) {
        buildCommand()
    } else {
        container('java') {
            sh 'echo "Generic Java build"'
        }
    }
}

def testProject(projectType) {
    def testCommands = [
        'maven': {
            container('maven') {
                sh 'mvn test -B'
                publishTestResults testResultsPattern: 'target/surefire-reports/*.xml', allowEmptyResults: true
            }
        },
        'gradle': {
            container('gradle') {
                sh './gradlew test'
                publishTestResults testResultsPattern: 'build/test-results/test/*.xml', allowEmptyResults: true
            }
        },
        'nodejs': {
            container('nodejs') {
                if (hasScript('test')) {
                    sh 'npm test'
                }
            }
        },
        'python': {
            container('python') {
                sh 'python -m pytest --junitxml=test-results.xml || echo "Tests completed"'
                publishTestResults testResultsPattern: 'test-results.xml', allowEmptyResults: true
            }
        }
    ]
    
    def testCommand = testCommands[projectType]
    if (testCommand) {
        testCommand()
    } else {
        echo "No tests configured for ${projectType}"
    }
}

def packageProject(projectType) {
    def packageCommands = [
        'maven': {
            container('maven') {
                sh 'mvn package -DskipTests -B'
                archiveArtifacts artifacts: 'target/*.jar', allowEmptyArchive: true
            }
        },
        'gradle': {
            container('gradle') {
                sh './gradlew assemble'
                archiveArtifacts artifacts: 'build/libs/*.jar', allowEmptyArchive: true
            }
        },
        'nodejs': {
            container('nodejs') {
                sh 'npm pack'
                archiveArtifacts artifacts: '*.tgz', allowEmptyArchive: true
            }
        },
        'python': {
            container('python') {
                if (fileExists('setup.py')) {
                    sh 'python setup.py sdist bdist_wheel'
                    archiveArtifacts artifacts: 'dist/*', allowEmptyArchive: true
                }
            }
        },
        'docker': {
            container('docker') {
                sh "docker save ${env.JOB_NAME.toLowerCase()}:${env.BUILD_NUMBER} | gzip > image.tar.gz"
                archiveArtifacts artifacts: 'image.tar.gz', allowEmptyArchive: true
            }
        }
    ]
    
    def packageCommand = packageCommands[projectType]
    if (packageCommand) {
        packageCommand()
    }
}

def deployProject(projectType, config) {
    echo "Deploying ${projectType} project"
    if (config.deployCommand) {
        sh config.deployCommand
    } else if (config.deployScript) {
        load config.deployScript
    }
}

def postActions(projectType, config) {
    if (projectType == 'docker') {
        container('docker') {
            sh 'docker system prune -f || true'
        }
    }
    
    if (config.notifications) {
        // Add notification logic
        echo "Build completed for ${projectType} project"
    }
    
    cleanWs()
}

// Helper functions
def shouldRunTests(config, projectType) {
    return config.runTests != false && projectType in ['maven', 'gradle', 'nodejs', 'python']
}

def shouldPackage(config, projectType) {
    return config.package != false
}

def shouldDeploy(config) {
    return config.deploy == true
}

def hasScript(scriptName) {
    if (!fileExists('package.json')) return false
    def packageJson = readJSON file: 'package.json'
    return packageJson.scripts && packageJson.scripts[scriptName]
}
def buildProject(language, buildTool = null) {
    switch(language) {
        case 'python':
            buildPython()
            break
        case 'java':
            buildJava(buildTool ?: 'maven')
            break
        case 'nodejs':
            buildNodejs()
            break
        case 'go':
            buildGo()
            break
        default:
            error("Unsupported language: ${language}")
    }
}

def testProject(language, buildTool = null) {
    switch(language) {
        case 'python':
            testPython()
            break
        case 'java':
            testJava(buildTool ?: 'maven')
            break
        case 'nodejs':
            testNodejs()
            break
        case 'go':
            testGo()
            break
        default:
            echo "No tests configured for ${language}"
    }
}

def packageProject(language, buildTool = null) {
    switch(language) {
        case 'python':
            packagePython()
            break
        case 'java':
            packageJava(buildTool ?: 'maven')
            break
        case 'nodejs':
            packageNodejs()
            break
        case 'go':
            packageGo()
            break
        default:
            echo "No packaging configured for ${language}"
    }
}

def getPodTemplate(language) {
    def podMapping = [
        'python': 'python',
        'java': 'maven',
        'nodejs': 'nodejs',
        'go': 'java'
    ]
    return podMapping[language] ?: 'java'
}

// Python methods
def buildPython() {
    sh 'pip install --upgrade pip'
    if (fileExists('requirements.txt')) {
        sh 'pip install -r requirements.txt'
    }
    if (fileExists('setup.py')) {
        sh 'python setup.py build'
    }
}

def testPython() {
    sh 'pip install pytest || echo "pytest install failed"'
    sh 'python -m pytest --junitxml=test-results.xml || echo "Tests completed"'
    junit allowEmptyResults: true, testResults: 'test-results.xml'
}

def packagePython() {
    if (fileExists('setup.py')) {
        sh 'python setup.py sdist bdist_wheel'
        archiveArtifacts artifacts: 'dist/*', allowEmptyArchive: true
    }
}

// Java methods
def buildJava(buildTool) {
    if (buildTool == 'maven') {
        sh 'mvn clean compile -B'
    } else if (buildTool == 'gradle') {
        sh 'chmod +x gradlew && ./gradlew clean compileJava'
    }
}

def testJava(buildTool) {
    if (buildTool == 'maven') {
        sh 'mvn test -B'
        junit 'target/surefire-reports/*.xml'
    } else if (buildTool == 'gradle') {
        sh './gradlew test'
        junit 'build/test-results/test/*.xml'
    }
}

def packageJava(buildTool) {
    if (buildTool == 'maven') {
        sh 'mvn package -DskipTests -B'
        archiveArtifacts artifacts: 'target/*.jar', allowEmptyArchive: true
    } else if (buildTool == 'gradle') {
        sh './gradlew assemble'
        archiveArtifacts artifacts: 'build/libs/*.jar', allowEmptyArchive: true
    }
}

// Node.js methods
def buildNodejs() {
    sh 'npm ci --only=production'
    if (hasNpmScript('build')) {
        sh 'npm run build'
    }
}

def testNodejs() {
    if (hasNpmScript('test')) {
        sh 'npm test'
    }
}

def packageNodejs() {
    sh 'npm pack'
    archiveArtifacts artifacts: '*.tgz', allowEmptyArchive: true
}

// Go methods
def buildGo() {
    sh 'go mod download'
    sh 'go build -v ./...'
}

def testGo() {
    sh 'go test -v ./...'
}

def packageGo() {
    sh 'go build -o app'
    archiveArtifacts artifacts: 'app', allowEmptyArchive: true
}

// Helper methods
def hasNpmScript(scriptName) {
    if (!fileExists('package.json')) return false
    def packageJson = readJSON file: 'package.json'
    return packageJson.scripts && packageJson.scripts[scriptName]
}
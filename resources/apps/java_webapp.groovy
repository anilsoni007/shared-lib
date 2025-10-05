return [
    language: 'java',
    buildTool: 'maven',
    gitUrl: 'https://github.com/example/java-webapp.git',
    gitBranch: 'main',
    stages: [
        build: true,
        test: true,
        package: true,
        deploy: [
            enabled: true,
            target: 'production',
            manifest: 'k8s/webapp.yaml'
        ]
    ]
]
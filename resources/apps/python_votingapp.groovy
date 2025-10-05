return [
    language: 'python',
    gitUrl: 'https://github.com/anilsoni007/simple-voting-app.git',
    gitBranch: 'main',
    stages: [
        build: true,
        test: true,
        package: true,
        deploy: [
            enabled: false,
            target: 'staging',
            manifest: 'k8s/voting-app.yaml'
        ]
    ]
]
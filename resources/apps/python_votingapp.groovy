return [
    podTemplate: 'python',
    gitUrl: 'https://github.com/anilsoni007/simple-voting-app.git',
    gitBranch: 'main',
    stages: [
        build: [
            enabled: true,
            commands: [
                'pip install --upgrade pip',
                'pip install -r requirements.txt'
            ]
        ],
        test: [
            enabled: true,
            commands: [
                'python -m pytest --junitxml=test-results.xml || echo "Tests completed"'
            ]
        ],
        package: [
            enabled: false
        ],
        deploy: [
            enabled: true,
            target: 'staging',
            commands: [
                'kubectl apply -f k8s/voting-app.yaml'
            ]
        ]
    ]
]
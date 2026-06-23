To Test MongoDb is Up
kubectl exec -n nagp2026-assignment -it mongodb-0 -- mongosh -u adminUser -p securePassword123 --eval "db.adminCommand('ping')"

To connect to MongoDb from a client pod
kubectl run mongo-test-client -n nagp2026-assignment --rm -it --image=mongo:6.0 -- bash
mongosh "mongodb://adminUser:securePassword12345@mongodb-0.mongodb-headless.nagp2026-assignment.svc.cluster.local:27017/?authSource=admin"


To connect to MongoDb from outside the cluster, you can use port forwarding:
kubectl port-forward pod/mongodb-0 27017:27017 -n nagp2026-assignment
Then, you can connect to MongoDb using a MongoDB client on your local machine:
mongodb://adminUser:securePassword12345@localhost:27017/?authSource=admin

To test the HPA, you can use the following command to generate load on the application:
kubectl run load-generator --rm -i --tty --image=busybox:1.36 --restart=Never -- /bin/sh -c "while true; do wget -q -O- http://8.233.80.16/api/v1/assignments --header 'Host: app.nagp2026-assignment.com'; done"
```
cf create-service p-mysql 100mb jobs
cf create-service scheduler-for-pcf standard scheduler

./mvnw clean package -DskipTests=true && cf push --vars-file=vars.yml
```

```
cf run-task mysqlbackup "$(cf curl /v3/apps/$(cf app mysqlbackup --guid)/droplets/current | jq -r .process_types.task) --spring.batch.job.enabled=true"
```


```
cf create-job mysqlbackup backup-job "$(cf curl /v3/apps/$(cf app mysqlbackup --guid)/droplets/current | jq -r .process_types.task) --spring.batch.job.enabled=true"
cf schedule-job backup-job "0 0 ? * *"
```
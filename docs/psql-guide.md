access psql

````
docker exec -it biletflow-postgresql-1 psql -U biletFlow
````

connect to db

```
\c biletFlow;
\dt
```

sometimes it's nice to enable expanded view

```
\x
```

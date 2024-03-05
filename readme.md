# Cloud APIM Moesif otoroshi plugin

## Plugin configuration

`app_id` is an alphanumeric value **field is mandatory**

`customer_key` is a json path **field is mandatory**

`company_key` is a json path **field is mandatory**

`action_name` field is _optional_

Example

 ```json
{
    "app_id": "your moesif app id here", 
    "customer_key": "$.metadata.email",
    "company_key": "$.metadata.email",
    "action_name": "whatever you want" 
}
```
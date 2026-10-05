# Templated Data Management service.
- Your role is design and develop a data management service.
- The service is a spring boot service. Use Java 21 compatible spingboot version. Java path `C:\InstalledSofts\jdk-21.0.11`
- Given a database schema relationship, each field map to UI element, role permissions and its type  then the service then the service has to create a table and a react UI for manage the data. This has to be happen without any code change.
- Once table created with entity relations, UI created, then when data added to the table there should be view UI available in a usable, easy data view tabular format.
- Some UI have actionable functions with injectable java scripts. Thos has to be store in a file and loaded to service. When service reloads and java scripts must be loaded.
- Roles need to be separately managed. Can be add new role, Assign to a user by admin. The template then map it to UI and schema. Auth keep simple as username and password for now.
- manage_type are `VIEW` where data added to table directly, UI display it. manage_type `MANAGE_VIEW` data edited via admin UI and viewers view it. manage_type `DATA_SOURCE` where data added directly to table, act as data to other tables/ ui, no manage UI.
- Every table data to add/ update / delete REST API must be available. UI access data via REST API.
- At first local development use sqlite as db, but compatible schema to postgres. Later migrate to postgres.
- Example pseudo schema as follows, suggest a usable format. This exmaple not include all roles, like viewer is default. Also constrints like null/ empty allowed etc...if x present y also should be present. I ll leave that for your planing, but they are required.
```
{
	manage_type:"VIEW",
	"schema_map":{
		"id": bigint, PK -> UI:NIL, Roles:Nil
		"alert_date": datetime-> UI: row_label,Roles:[Admin->Add, Update, Delete]
		"alert_name": varchar(255)-> UI:row_lbel,Roles:[Admin->Add, Update, Delete]
		"alert_type": enum ->UI: row_label,Roles:[Admin->Add, Update, Delete]
		"alert_group": varchar(255) via FK Table alert_groups-> UI:row_label,Roles:[Admin->Add, Update, Delete]
		"alert_source":Text -> UI: Label,Roles:[Admin->Add, Update, Delete]
		"alert_site": Text -> UI: Label,Roles:[Admin->Add, Update, Delete]
		"alert_description": Text -> UI: Label,Roles:[Admin->Add, Update, Delete]
		"alert_ticket":Text -> UI: Label,Roles:[Admin->Add, Update, Delete]
		"create_ticket": -> UI: button[Action-> JavaScript_Function("<complete java script function runs in Java. For this example add java script create a simple text ticket file which can be copy pasted to Jira with alert details>)
	}
}
	
```
```
{
	manage_type:"DATA_SOURCE",
	"schema_map":{
		"id": bigint, PK -> UI:NIL, Roles:[Admin->Add, Update, Delete]
		"alert_group_name": varchar(255)-> UI:Nil,Roles:[Admin->Add, Update, Delete]
	}
}

```
```
{
	manage_type:"MANAGE_VIEW",
	"schema_map":{
		"id": bigint, PK -> UI:NIL, Roles:Nil
		"operator_name": datetime-> UI: text,Roles:[Admin->Add, Update, Delete]
		"jurisdictional_name": varchar(255)-> UI:text,Roles:[Admin->Add, Update, Delete]
		"minSpinTime": float ->UI: text,Roles:[Admin->Add, Update, Delete]
	}
}
```
- Now we got the overall idea we need to define the schema vocabulary and design a schema UI to manage the schemas.
# stream-call

Uses the https://getstream.io/ SDK to implement calling in Capacitor

## Install

```bash
npm install stream-call
npx cap sync
```

## API

<docgen-index>

* [`echo(...)`](#echo)
* [`initialize()`](#initialize)
* [`login(...)`](#login)
* [`logout()`](#logout)
* [`call(...)`](#call)
* [Interfaces](#interfaces)

</docgen-index>

<docgen-api>
<!--Update the source file JSDoc comments and rerun docgen to update the docs below-->

### echo(...)

```typescript
echo(options: { value: string; }) => Promise<{ value: string; }>
```

| Param         | Type                            |
| ------------- | ------------------------------- |
| **`options`** | <code>{ value: string; }</code> |

**Returns:** <code>Promise&lt;{ value: string; }&gt;</code>

--------------------


### initialize()

```typescript
initialize() => Promise<void>
```

--------------------


### login(...)

```typescript
login(options: LoginOptions) => Promise<SuccessResponse>
```

| Param         | Type                                                  |
| ------------- | ----------------------------------------------------- |
| **`options`** | <code><a href="#loginoptions">LoginOptions</a></code> |

**Returns:** <code>Promise&lt;<a href="#successresponse">SuccessResponse</a>&gt;</code>

--------------------


### logout()

```typescript
logout() => Promise<SuccessResponse>
```

**Returns:** <code>Promise&lt;<a href="#successresponse">SuccessResponse</a>&gt;</code>

--------------------


### call(...)

```typescript
call(options: CallOptions) => Promise<SuccessResponse>
```

| Param         | Type                                                |
| ------------- | --------------------------------------------------- |
| **`options`** | <code><a href="#calloptions">CallOptions</a></code> |

**Returns:** <code>Promise&lt;<a href="#successresponse">SuccessResponse</a>&gt;</code>

--------------------


### Interfaces


#### SuccessResponse

| Prop          | Type                 |
| ------------- | -------------------- |
| **`success`** | <code>boolean</code> |


#### LoginOptions

| Prop           | Type                |
| -------------- | ------------------- |
| **`token`**    | <code>string</code> |
| **`userId`**   | <code>string</code> |
| **`name`**     | <code>string</code> |
| **`imageURL`** | <code>string</code> |


#### CallOptions

| Prop         | Type                 |
| ------------ | -------------------- |
| **`userId`** | <code>string</code>  |
| **`type`**   | <code>string</code>  |
| **`ring`**   | <code>boolean</code> |

</docgen-api>

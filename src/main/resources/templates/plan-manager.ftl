<#-- @ftlvariable name="title" type="java.lang.String" -->

<#import "/spring.ftl" as spring/>
<#import "components/loading.ftl" as loading/>
<#import "components/vue-loader.ftl" as vueLoader/>
<#import "components/menu.ftl" as menu/>
<#import "components/footer.ftl" as footer/>

<!DOCTYPE HTML>
<html lang="de">
<head>
    <title>Vertretungsplan - Stephaneum</title>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <link rel="icon" type="image/png" href="/static/img/favicon.png" />
    <link rel="apple-touch-icon" sizes="196x196" href="/static/img/favicon.png">
    <link rel="stylesheet" type="text/css" href="/static/css/materialize.min.css">
    <link rel="stylesheet" type="text/css" href="/static/css/material-icons.css">
    <link rel="stylesheet" type="text/css" href="/static/css/style.css">
    <style>
        [v-cloak] {
            display: none;
        }

        .round-area {
            display: flex;
            align-items: center;
            background-color: #e8f5e9;
            border-radius: 20px;
            padding: 20px;
        }

        .round-area > div > h5 {
            margin: 0;
        }

        .round-area > i {
            font-size: 4em;
            margin-right: 20px;
        }

        .info-text {
            display: inline-block;
            font-style: italic;
            margin-top: 10px
        }

        .quick-button {
            background: #1b5e20;
        }

        .quick-button:hover {
            background: #2e7d32;
        }
    </style>
</head>

<body>

<@vueLoader.blank/>
<div id="app" style="display: flex; align-items: center; flex-direction: column; min-height: calc(100vh + 100px)" v-cloak>
    <nav-menu :menu="info.menu" :user="info.user" :plan="info.plan" :unapproved="info.unapproved"></nav-menu>
    <div v-if="allowed" style="flex: 1; display: flex; align-items: center; justify-content: center">
        <div style="width: 900px;">
            <div style="text-align: center; margin-bottom: 40px">
                <i class="material-icons" style="font-size: 4em">description</i>
                <h4 style="margin: 5px 0 0 0">Vertretungsplan</h4>
            </div>

            <div class="card-panel" style="display: flex; padding: 30px 0 30px 30px">
                <div style="flex: 50%">
                    <div class="round-area">
                        <i class="material-icons">description</i>
                        <div>
                            <h5 style="margin-bottom: 10px">PDF-Datei</h5>
                            <form method="POST" enctype="multipart/form-data">
                                <input name="file" type="file" id="upload-pdf" @change="upload($event.currentTarget.files[0])" style="display: none">

                                <a class="waves-effect waves-light tooltipped green darken-3 btn"
                                   @click="showUpload" data-tooltip="Hochladen" data-position="bottom">
                                    <i class="material-icons">cloud_upload</i>
                                </a>
                                <a class="waves-effect waves-light tooltipped red darken-3 btn" style="margin-left: 10px"
                                   @click="showDelete" :disabled="!info.plan.exists" data-tooltip="Löschen" data-position="bottom">
                                    <i class="material-icons">delete</i>
                                </a>
                            </form>
                            <span v-if="lastModified" class="info-text">Stand: {{ lastModified }}</span>
                            <span v-else class="info-text">(keine Datei bereitgestellt)</span>
                        </div>
                    </div>
                    <div class="round-area" style="margin-top: 30px">
                        <i class="material-icons">info</i>
                        <div>
                            <h5>Zusatzinformation</h5>
                            <div style="display: flex; align-items: center;">
                                <div class="input-field" style="width: 200px; margin-bottom: 0">
                                    <input v-model:value="planInfo" type="text" id="plan-info" />
                                </div>
                                <a v-show="planInfo !== info.plan.info" style="margin-left: 20px" class="waves-effect waves-light tooltipped green darken-3 btn"
                                   @click="updateText" data-tooltip="Speichern" data-position="bottom">
                                    <i class="material-icons">save</i>
                                </a>
                            </div>
                        </div>
                    </div>
                </div>
                <div style="flex: 50%; display: flex; align-items: center; justify-content: center; flex-direction: column">
                    <h5 style="text-align: center; margin-bottom: 30px">Vorschau</h5>
                    <div v-if="info.plan.exists" style="width: 330px">
                        <a href="vertretungsplan.pdf" target="_blank">
                            <div class="quick-button card">
                                <div class="card-content white-text">
                                    <div class="row" style="margin-bottom:0">
                                        <div class="col s12 m12 l8">
                                            <span class="card-title">Vertretungsplan</span>
                                            <p>{{ planInfo }}</p>
                                        </div>
                                        <div class="col l4 right-align hide-on-med-and-down">
                                            <i id="quick-icon" class="material-icons" style="font-size:50pt">description</i>
                                        </div>
                                    </div>
                                </div>
                            </div>
                        </a>
                    </div>
                    <span class="green-badge-light" style="font-size: 1em; margin-top: 20px" v-else>ausgeblendet</span>
                </div>


            </div>
        </div>
    </div>
    <div v-else style="flex: 1"></div>

    <stephaneum-footer :copyright="info.copyright"></stephaneum-footer>

    <!-- delete modal -->
    <div id="modal-delete" class="modal">
        <div class="modal-content">
            <h4>PDF-Datei wirklich löschen?</h4>
            <p>Dieser Vorgang kann nicht rückgangig gemacht werden.</p>
        </div>
        <div class="modal-footer">
            <a @click="closeDelete" href="#!" class="modal-close waves-effect waves-green btn-flat">Abbrechen</a>
            <a @click="doDelete" href="#!" class="modal-close waves-effect waves-red btn red darken-4">
                <i class="material-icons left">delete</i>
                Löschen
            </a>
        </div>
    </div>
</div>

<script src="/static/js/materialize.min.js" ></script>
<script src="/static/js/axios.min.js" ></script>
<script src="/static/js/vue.js" ></script>
<@loading.render/>
<@menu.render/>
<@footer.render/>
<script type="text/javascript">
    var app = new Vue({
        el: '#app',
        data: {
            info: { user: null, menu: null, plan: null, copyright: null, unapproved: null },
            planInfo: null,
            lastModified: null
        },
        methods: {
            showUpload: function() {
                document.getElementById('upload-pdf').click();
            },
            upload: function(file) {
                showLoading('Hochladen (0%)');
                var data = new FormData();
                data.append('file', file);
                var config = {
                    onUploadProgress: function(progressEvent) {
                        var percentCompleted = Math.round( (progressEvent.loaded * 100) / progressEvent.total );
                        showLoading('Hochladen ('+ percentCompleted +'%)', percentCompleted);
                    }
                };
                var instance = this;
                axios.post('./api/plan/upload', data, config)
                    .then(function (res) {
                        if(res.data.success) {
                            M.toast({ html: res.data.message });
                            instance.fetchData();
                        } else if(res.data.message) {
                            M.toast({ html: res.data.message });
                            hideLoading();
                        } else {
                            M.toast({ html: 'Ein Fehler ist aufgetreten.' });
                            hideLoading();
                        }
                    })
                    .catch(function (err) {
                        M.toast({ html: 'Ein Fehler ist aufgetreten.' });
                        console.log(err);
                        hideLoading();
                    });
            },
            showDelete: function(boardID, boardType) {
                this.boardDelete = { boardID, boardType };
                M.Modal.getInstance(document.getElementById('modal-delete')).open();
            },
            closeDelete: function() {
                M.Modal.getInstance(document.getElementById('modal-delete')).close();
            },
            doDelete: function() {
                showLoadingInvisible();
                axios.post('./api/plan/delete',)
                    .then((res) => {
                        if(res.data.success) {
                            M.toast({ html: 'Gelöscht.' });
                            this.fetchData();
                        } else if(res.data.message) {
                            M.toast({ html: res.data.message });
                            hideLoading();
                        } else {
                            M.toast({ html: 'Ein Fehler ist aufgetreten.' });
                            hideLoading();
                        }
                    });
            },
            updateText: function() {
                showLoadingInvisible();
                axios.post('./api/plan/text?text='+this.planInfo)
                    .then((res) => {
                        if(res.data.success) {
                            M.toast({ html: 'Änderungen gespeichert.' });
                            this.fetchData();
                        } else if(res.data.message) {
                            M.toast({ html: res.data.message });
                            hideLoading();
                        } else {
                            M.toast({ html: 'Ein Fehler ist aufgetreten.' });
                            hideLoading();
                        }
                    });
            },
            fetchData: function() {
                axios.get('./api/info',)
                    .then((res) => {
                        if(res.data) {
                            this.info = res.data;
                            this.planInfo = res.data.plan.info; // update text field
                            axios.get('./api/plan/last-modified')
                                .then((res) => {
                                    this.lastModified = res.data.message;
                                    hideLoading();
                                    this.$nextTick(() => M.Tooltip.init(document.querySelectorAll('.tooltipped'), {}));
                                });
                        } else {
                            M.toast({html: 'Interner Fehler.'});
                        }
                    });
            }
        },
        computed: {
            allowed: function() {
                return this.info.user && (this.info.user.code.role === 100 || this.info.user.managePlans)
            }
        },
        mounted: function() {
            M.AutoInit();
            this.fetchData();
        }
    });
</script>
</body>
</html>
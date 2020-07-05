#include <ESP8266WiFi.h>        // Include the Wi-Fi library
#include <EEPROM.h>

#define BUILT_IN_LED 2


unsigned int mapaLedsModoClienteConnected        = 0b00000000001111000000000010101;
unsigned int mapaLedsModoClienteDisconnected     = 0b00000000001111000000000010101;
unsigned int mapaLedsModoAPSemConexaoAtiva       =  0b11111111110000000000; // 20 bits
unsigned int mapaLedsModoAPConexaoAtivaSemClient = 0b1100;	// 4 BITS
unsigned int mapaLedsModoAPConexaoAtivaComClient = 0b111000111000111000;	// 18 BITS
unsigned int mapaLedsModoClientConexaoAtivaComSocket       =  0b00110011001100110011; // 20 bits
unsigned int mapaLedsModoClientConexaoAtivaSemClient       =  0b000011110000111100001111; // 24 bits



#define	ACCESS_POINT_STARTUP	'S'
#define	ACCESS_POINT			'A'
#define	CLIENT					'C'
 

// ===== Configuração ===============
#define	MAX_LEN		30

char strSSID[MAX_LEN + 1];
char strPasswd[MAX_LEN + 1];
long timeoutConexaoAsClient      = 20; // segundos
long timeoutConexaoAsAccessPoint = 60 * 10; // segundos
long timeoutConnectedAguardandoSocket	= 0;

#define	PARAM_CONFIG_SSID				    1
#define	PARAM_CONFIG_PASSWD				    2
#define	PARAM_TIMEOUT_AS_CLIENT 		    3
#define	PARAM_TIMEOUT_AS_ACCESS_POINT 	    4
#define	PARAM_TIMEOUT_CLIENT_WAITING_SOCKET 5

void gravaConfigEEPROM(void);
void recuperaConfigEEPROM(void);
void salvaConfigEEPROM(int flagZera);

int operationMode;
char *strErroConexao="";

//---------------------------------------------------------
// readThermometer
//---------------------------------------------------------
float readThermometer(void) 
{	int valor;
	valor = random(360, 400);
	return((float)valor/(float)10);
}

//---------------------------------------------------------
// accessPointInit
//---------------------------------------------------------
bool accessPointInit() 
{
	IPAddress local_IP(192,168,4,1);
	IPAddress gateway(192,168,4,1);
	IPAddress subnet(255,255,255,0);

	char *SSID = "8266_THERMOMETER";
	char *PASSWD = "nana12345";
	
	Serial.print("Ativando AccessPoint ");	Serial.println(SSID);

	if ( WiFi.softAPConfig(local_IP, gateway, subnet) ) {
		if ( WiFi.softAP(SSID, PASSWD) ) {
			Serial.print("Soft-AP IP address = "); 	Serial.println(WiFi.softAPIP());
			return true;
		} else {
			Serial.println( "Falha ao ativar AccessPoint.");
		}
	}  else {
		Serial.println( "Falha ao configurar AccessPoint.");
	}
	
	return false;
}

//---------------------------------------------------------
// wifiDeviceInit
//---------------------------------------------------------
bool wifiDeviceInit(char *SSID, char *PASSWD, int timeout) 
{	long start;
	int fim;
	int conta=0;
	int tempoRestante = timeout;

	time(&start);

    Serial.print("Entrando em wifiDeviceInit. Time="); Serial.println(start, DEC);

	// Inicialmente vamos tentar conectar na rede configurada
	WiFi.begin(SSID, PASSWD); 

	fim = 0;
	while( (! fim) && (tempoRestante > 0)  ) {
		switch( WiFi.status() ) {
			case WL_CONNECTED :
				fim = 1;
				break;
			case WL_NO_SSID_AVAIL :
			case WL_IDLE_STATUS : 
				break;
				
			case WL_CONNECT_FAILED :
				fim = 1;
				break;
		}
		PiscaLed(mapaLedsModoClienteDisconnected, 30, 100);
		delay(100);
		tempoRestante = timeout - (time(NULL) - start);
		if ( (conta++ % 10) == 0 ) {
	    	Serial.print("wifiDeviceInit. timeout="); Serial.println(tempoRestante, DEC);
		}
	}

	if ( WiFi.status() == WL_CONNECTED) {
		strErroConexao = NULL;
  		Serial.print("Conectado com sucesso na rede '");
  		Serial.print(SSID);
  		Serial.print("' IP: ");
  		Serial.println(WiFi.localIP());
  		return(true); // Conectado a rede configurada OK
	}
	
	switch(WiFi.status()) {
		case WL_NO_SSID_AVAIL 	: strErroConexao = "Não localizou SSID"; break;
		case WL_IDLE_STATUS 	: strErroConexao = "Timeout na conexão"; break;
		case WL_CONNECT_FAILED 	: strErroConexao = "Senha invalida"; break;
		default 				: strErroConexao = "Falha na conexão"; break;
	}

    Serial.println(strErroConexao);

    return(false);
}

//---------------------------------------------------------
// setup
//---------------------------------------------------------
void setup() {
	char c1, c2;

	pinMode(BUILT_IN_LED, OUTPUT);
	
	Serial.begin(115200);
	delay(3000);
	Serial.println( "\r\nINICIANDO....");	

	operationMode=ACCESS_POINT_STARTUP;

}

//---------------------------------------------------------
// loop
//---------------------------------------------------------
void loop()
{
	
    Serial.print("Loop operationMode = "); Serial.println(operationMode, DEC);

	if ( operationMode == ACCESS_POINT_STARTUP ) {
		processaModoAccessPoint(5);
		recuperaConfigEEPROM();
		if  ( strSSID[0] != '\0' ) {
			// Inicialmente vamos tentar conectar na rede configurada
			operationMode=CLIENT;
		} else {
			operationMode=ACCESS_POINT;
		}
	} else if ( operationMode == CLIENT) {
		processaModoClient();
	} else  {
		processaModoAccessPoint(timeoutConexaoAsAccessPoint);
	} 
}


//---------------------------------------------------------
// PiscaLed
//---------------------------------------------------------
void PiscaLed(unsigned int novoMapa, int qtdBits, int frequencia) 
{	static unsigned int mapa;
	static unsigned int valorCorrente;
	static int qtdBitsMapeado;
	static unsigned int conta=0;
	static int tempo100ms;
	static int frequenciaAnt;
	bool ret;

	// Inicia nova sequencia
	if ( (novoMapa != mapa) || (qtdBitsMapeado != qtdBits) || (frequencia != frequenciaAnt) ) {
		valorCorrente = mapa = novoMapa;
		qtdBitsMapeado = qtdBits;
		frequenciaAnt = frequencia;
		conta=0;
		tempo100ms = 100;
		return;
	}

	tempo100ms -= frequencia;

	if ( tempo100ms > 0 ) {
		return;
	}

	tempo100ms = 100;
	ret = valorCorrente & 0x1;
	
	if ( ++conta == qtdBitsMapeado ) {
		conta = 0;	
		valorCorrente = mapa;
	} else {
		valorCorrente = valorCorrente >> 1;
	}

	digitalWrite(BUILT_IN_LED, (ret) ? LOW: HIGH);  
}

//---------------------------------------------------------
// esperaLinhaDoSocket
//---------------------------------------------------------
char *esperaLinhaDoSocket(WiFiClient *client)
{	static int indRx;
	static char linhaRecebidaDoSocket[300];
	char c;

	if ( client == NULL  ) {
		memset(linhaRecebidaDoSocket, 0, sizeof(linhaRecebidaDoSocket));
		indRx=0;
		return(NULL);
	}

	while (client->available() > 0) {
		c = client->read();
		if (c == '\r') continue;
		// Processa linha recebida
		if ( c != '\n' ) {
			if ( indRx >= sizeof(linhaRecebidaDoSocket) ) {
				continue;
			} else {
				// armazena caracter recebido na linha a ser processada
				linhaRecebidaDoSocket[indRx++] = c;
			}
		} else {
			if ( indRx >= sizeof(linhaRecebidaDoSocket) ) {
				// Linha recebida excedeu tamanho máximo, despreza a linha
				memset(linhaRecebidaDoSocket, 0, sizeof(linhaRecebidaDoSocket));
				indRx = 0;
				continue;
			} else {
				if ( indRx > 0 ) {
					linhaRecebidaDoSocket[indRx] = '\0';
					indRx = 0;
					return(linhaRecebidaDoSocket);
				}
			}
		}
	}

	return(NULL);
}

//---------------------------------------------------------
// processaSocketConnected
//---------------------------------------------------------
void processaSocketConnected(WiFiClient client, long timeoutSemSocket)
{	char strResposta[100];
	long startConnection;

	time(&startConnection);

	esperaLinhaDoSocket(NULL); // Inicializa para receber linhas
	
	while ( client.connected()  ) { // && (WiFi.status() == WL_CONNECTED)
		PiscaLed(mapaLedsModoClientConexaoAtivaComSocket, 18, 10);
      	while (client.available() > 0) {
			char *linhaRecebida = esperaLinhaDoSocket(&client);
			if ( linhaRecebida != NULL ) {
				Serial.println(linhaRecebida);
				if ( strcmp(linhaRecebida, "IP") == 0 ) {
					sprintf(strResposta, "IP=%d.%d.%d.%d\r\n", WiFi.localIP()[0],  WiFi.localIP()[1] ,  WiFi.localIP()[2] ,  WiFi.localIP()[3] );
	        		client.write(strResposta);
	        		client.flush();
			    	Serial.print(strResposta);
				} else if ( strcmp(linhaRecebida, "TEMP") == 0 ) {
					sprintf(strResposta, "TEMP=%.2f\r\n", readThermometer());
	        		client.write(strResposta);
	        		client.flush();
			    	Serial.print(strResposta);
				} else if ( strcmp(linhaRecebida, "RESET") == 0 ) {
					sprintf(strResposta, "RESET\r\n");
	        		client.write(strResposta);
	        		client.flush();
			    	hardwareReset("Comando RESET");
				} else if ( strncmp(linhaRecebida, "CONFIG:", 7) == 0 ) {
					char *resp;
					if ( (resp = trataLinhaConfiguracao(&linhaRecebida[7])) != NULL ) {
						client.write(resp);
		        		WiFi.disconnect(0);
						hardwareReset("Nova configuração recebida");
					} else {
						client.write("LINHA CONFIGURACAO INVALIDA");
					}
				} else if ( strcmp(linhaRecebida, "PING") == 0 ) {
					sprintf(strResposta, "PONG\r\n");
	        		client.write(strResposta);
	        		client.flush();
			    	Serial.print(strResposta);
				} else if ( strcmp(linhaRecebida, "BYE") == 0 ) {
					Serial.println("Executando BYE");
					client.stop();
				} else if ( strcmp(linhaRecebida, "ZERA") == 0 ) {
					Serial.println("Executando ZERA");
					sprintf(strResposta, "ZERA\r\n");
	        		client.write(strResposta);
	        		client.flush();
	        		salvaConfigEEPROM(1);
	        		WiFi.disconnect(0);
			    	hardwareReset("Comando RESET");
				} else {
			    	Serial.print("linhaRecebida: ["); Serial.print(linhaRecebida); Serial.println("]");
				}
			}
		}
		delay(10);
	}

	Serial.println("encerrando  processaSocketConnected");
}

	WiFiServer 		wifiServerAP(81);
	WiFiServer 		wifiServerClient(80);

//---------------------------------------------------------
// processaAguardandoSocket
//---------------------------------------------------------
void processaAguardandoSocket(int modoOperacao)
{	long timeUltimaConexaoSocket;
	int conta=0;
	char strResposta[100];
	long startListen;
	long tempoEsperando;
	long timeoutSemSocket;
	int flag=0;


	Serial.println((WiFi.status() == WL_CONNECTED) ? "WL_CONNECTED 111" : "nao WL_CONNECTED 111");

	time(&timeUltimaConexaoSocket);
    Serial.print("Setando timeUltimaConexaoSocket="); Serial.println(timeUltimaConexaoSocket, DEC);

	switch(modoOperacao) {
		case ACCESS_POINT_STARTUP : 
	    	timeoutSemSocket = timeoutConexaoAsAccessPoint;
			wifiServerAP.begin();
			break;
		case ACCESS_POINT : 
	    	timeoutSemSocket = timeoutConexaoAsAccessPoint;
			wifiServerAP.begin();
			break;
		case CLIENT : 
	    	timeoutSemSocket = 0;
			wifiServerAP.begin();
			wifiServerClient.begin();
			break;
	}

	Serial.print("Connected to wifi. My address:");
	IPAddress myAddress = WiFi.localIP();
	Serial.println(myAddress);


	time(&startListen);

	while ( 1 ) {
		
		if ( modoOperacao == ACCESS_POINT ) {
			if ( WiFi.softAPgetStationNum() == 0 ) {
				break;
			}
		}

		WiFiClient client = ( (modoOperacao == CLIENT) && (flag++ & 0x1 )) ? wifiServerClient.available() : wifiServerAP.available();

		if ( client ) {
			processaSocketConnected(client, 0);
	    	time(&timeUltimaConexaoSocket);
		    Serial.print("Setando timeUltimaConexaoSocket="); Serial.println(timeUltimaConexaoSocket, DEC);
		    conta=0;
		} else {
			PiscaLed(mapaLedsModoClientConexaoAtivaSemClient, 24, 100);
			delay(100);
			tempoEsperando = time(NULL) - startListen;
			if ( timeoutSemSocket != 0) {
				if ( tempoEsperando > timeoutSemSocket  ) {
					hardwareReset("Timeout aguardando Socket");
				}
			}
			if ( (conta++ % 10) == 0) {
				char strIP[30];
				sprintf(strIP, "IP=%d.%d.%d.%d ", WiFi.localIP()[0],  WiFi.localIP()[1] ,  WiFi.localIP()[2] ,  WiFi.localIP()[3] );

				if ( timeoutSemSocket != 0 ) {
					Serial.print("Aguardando cliente "); Serial.print(strIP);  Serial.println(timeoutSemSocket - tempoEsperando, DEC); 
				} else {
					Serial.print("Aguardando cliente ");  Serial.print(strIP);  Serial.println(time(NULL) - timeUltimaConexaoSocket, DEC); 
				}
			}
		}
	}


	switch(modoOperacao) {
		case ACCESS_POINT_STARTUP : 
			wifiServerAP.stop();
			break;
		case ACCESS_POINT : 
			wifiServerAP.stop();
			break;
		case CLIENT : 
			wifiServerClient.stop();
			break;
	}
	
}

//---------------------------------------------------------
// processaModoClient
//---------------------------------------------------------
void processaModoClient(void)
{
	if ( wifiDeviceInit(strSSID, strPasswd, timeoutConexaoAsClient)  ) {
		processaAguardandoSocket(CLIENT);
	}
    Serial.println("Saindo Modo Cliente.\n");
}


//---------------------------------------------------------
// processaModoAccessPoint
//---------------------------------------------------------
void processaModoAccessPoint(long timeout)
{	static int viuAlguem=0;
	int i;
	long now;
	long entradaEmModoAccessPoint;
	int conta = 0;

	time(&entradaEmModoAccessPoint);

	if ( ! accessPointInit() ) {
		hardwareReset("Falha em  WiFi.softAP");
	}
	
	Serial.println("WORKING_AS_ACCESS_POINT");

	while ( 1 ) {
		
		if ( (time(NULL) - entradaEmModoAccessPoint) > timeout  ) {
			if ( operationMode == ACCESS_POINT_STARTUP ) {
				Serial.println("Encerrando Modo ACCESS_POINT_STARTUP");
				return;	
			} else {
				hardwareReset("Timeout para operar como AccessPoint");
			}
		}

		if ( WiFi.softAPgetStationNum() == 0 ) {
			PiscaLed(mapaLedsModoAPSemConexaoAtiva, 20, 100);
			delay(100);
			if ( (conta++ % 10) == 0 ) {
				Serial.print("Conexoes ativas : "); Serial.println(WiFi.softAPgetStationNum(), DEC); 
			}
			continue;
		} else {
			Serial.print("Conexoes ativas : "); Serial.println(WiFi.softAPgetStationNum(), DEC); 
				Serial.println((WiFi.status() == WL_CONNECTED) ? "WL_CONNECTED 000" : "nao WL_CONNECTED 000");

			processaAguardandoSocket( ACCESS_POINT);
			time(&entradaEmModoAccessPoint);
		}
	}

}

//---------------------------------------------------------
// trataLinhaConfiguracao
//---------------------------------------------------------
char *trataLinhaConfiguracao(char *linha)
{	int tam;
	char *start;
	char *p;
	int indConfig=0;
	int fim = 0;
	int indLinha=1;
	int erro = 0;
	static char resposta[100];
	uint8_t macAddr[6];


	if ( (p = strchr(linha, '\r')) != NULL) {
		*p = '\0'; 
	}
	if ( (p = strchr(linha, '\n')) != NULL) {
		*p = '\0'; 
	}
 	tam = strlen(linha);
 
	if ( linha[0] != '[') return(0); 
	if ( tam < 10 ) return(0); 
	if ( linha[tam -1] != ']') return(0);

	linha[tam -1] = '\0';

	p = start = &linha[1];

	Serial.print("trataLinhaConfiguracao = <<<"); 
	Serial.print(start); 
	Serial.println(">>>");

	
	p = start = &linha[1];
	tam = 0;
	fim = 0;
	while ( ! fim ) {

		// Anda até achar um TAB ou fim da linha
		if ( (*p != '\t') && ( *p != '\0' )) {
			p++;
			continue;
		}
		
		*p++ = '\0';

		indConfig++;
		Serial.print("indConfig = ");  Serial.println(indConfig, DEC);
		Serial.print("start = ");  Serial.println(start);

		// Processa parametro
		switch(indConfig) {
			case PARAM_CONFIG_SSID  			     : strcpy(strSSID, start); break;
			case PARAM_CONFIG_PASSWD  			     : strcpy(strPasswd, start);  break;
			case PARAM_TIMEOUT_AS_CLIENT  		     : timeoutConexaoAsClient = atoi(start); break;
			case PARAM_TIMEOUT_AS_ACCESS_POINT       : timeoutConexaoAsAccessPoint = atoi(start); break;
			case PARAM_TIMEOUT_CLIENT_WAITING_SOCKET : timeoutConnectedAguardandoSocket = atoi(start); break;
			default: 
				fim = 1;
				break;
		}
		start = p;
	}

	if ( (timeoutConexaoAsClient <= 0) || (timeoutConexaoAsClient > 60) ) {
		Serial.println("timeoutConexaoAsClient Invalido");
		timeoutConexaoAsClient = 20;
		erro = 1;
	}

	if ( (timeoutConexaoAsAccessPoint <= 0) || (timeoutConexaoAsAccessPoint > 60) ) {
		Serial.println("timeoutConexaoAsClient Invalido");
		timeoutConexaoAsAccessPoint = 60;
		erro = 1;
	}

	if ( (timeoutConnectedAguardandoSocket <= 0) || (timeoutConnectedAguardandoSocket > (5 * 60)) ) {
		timeoutConnectedAguardandoSocket = 0;
	}


	Serial.print("strSSID                          : "); Serial.println(strSSID);
	Serial.print("strPasswd                        : "); Serial.println(strPasswd);
	Serial.print("timeoutConexaoAsClient           : "); Serial.println( timeoutConexaoAsClient, DEC );
	Serial.print("timeoutConexaoAsAccessPoint      : "); Serial.println( timeoutConexaoAsAccessPoint, DEC );
	Serial.print("timeoutConnectedAguardandoSocket : "); Serial.println( timeoutConnectedAguardandoSocket, DEC );

	delay(500);
	
	if ( erro == 0 ) {
		
		salvaConfigEEPROM(0);

		WiFi.macAddress(macAddr); // Vamos pegar o Mac do "Client" e não do "AccessPoint"
		sprintf(resposta, "%02x:%02x:%02x:%02x:%02x:%02x\r\n", macAddr[0], macAddr[1], macAddr[2], macAddr[3], macAddr[4], macAddr[5]);
		return(resposta);
	}
	return(NULL);
}


//---------------------------------------------------------
// salvaConfigEEPROM
//---------------------------------------------------------
void salvaConfigEEPROM(int flagZera)
{
	int indEEPROM=0;
	int indConfig=0;
	char *p;
	int fim = 0;
	char aux[30];

	if ( flagZera == 0) {
		while ( ! fim ) {
			indConfig++;
			p=NULL;
			switch(indConfig) {
				case PARAM_CONFIG_SSID   			        : p = strSSID; break;
				case PARAM_CONFIG_PASSWD 			        : p = strPasswd; break;
				case PARAM_TIMEOUT_AS_CLIENT         		: sprintf(aux, "%d", timeoutConexaoAsClient); p = aux; break;
				case PARAM_TIMEOUT_AS_ACCESS_POINT 	        : sprintf(aux, "%d", timeoutConexaoAsAccessPoint); p = aux; break;
				case PARAM_TIMEOUT_CLIENT_WAITING_SOCKET 	: sprintf(aux, "%d", timeoutConnectedAguardandoSocket); p = aux; break;
				default : fim = 1;			
			}
	
			if ( p ) {
				EEPROM.write(indEEPROM++, '[');
				while ( *p ) {
					EEPROM.write(indEEPROM++, *p);
					p++;
				}
				EEPROM.write(indEEPROM++, ']');
				EEPROM.write(indEEPROM++, '\0');
			}
		}
		
		EEPROM.write(indEEPROM++, 0x03 ); // EOT
	} else {
		for ( indEEPROM=0; indEEPROM<128; indEEPROM++) {
			EEPROM.write(indEEPROM, 255);
		}
	}

	EEPROM.commit();
}

//---------------------------------------------------------
// recuperaConfigEEPROM
//---------------------------------------------------------
void recuperaConfigEEPROM(void)
{	int linha=0;
	int indEEPROM=0;
	int indAux=0;
	char aux[MAX_LEN];
	unsigned char c;
	int indConfig=0;
	int erro=0;

	// Inicialização do tratamento da  EEPROM
	EEPROM.begin(128);	// Se aumentar quantidade de parametros de configuração, precisamos aumentar esse tamanho

	int fim = 0;
	while ( (erro == 0) && (fim==0) ) {
		c = EEPROM.read(indEEPROM++);
		if ( (c == 255) || (c == 0x03) ) break; // EOT ou -1

		if ( indAux >= MAX_LEN ) {
			erro = 1;
			break;
		}
		
		aux[indAux++] = c;
		
		if ( c == 0 ) {
			++indConfig;
			indAux--;
			Serial.print("indConfig = ");  Serial.println(indConfig, DEC);
			Serial.print("aux[indAux-1] = ");  Serial.println(aux[indAux-1], DEC);
			Serial.print("aux = ");  Serial.println(aux);

			switch(indConfig) {
				case PARAM_CONFIG_SSID  :
					if ( (aux[0] == '[') && (aux[indAux-1] == ']') ) {
						aux[indAux-1] = '\0';
						strcpy(strSSID, &aux[1]);
					} else {
						erro = 1;
					}
					break;
					
				case PARAM_CONFIG_PASSWD :
					if ( (aux[0] == '[') && (aux[indAux-1] == ']') ) {
						aux[indAux-1] = '\0';
						strcpy(strPasswd, &aux[1]);
					} else {
						erro = 1;
					}
					break;

				case PARAM_TIMEOUT_AS_CLIENT :
					if ( (aux[0] == '[') && (aux[indAux-1] == ']') ) {
						int val = atoi(&aux[1]);
						if ( (val > 0) && (val<=60) ) {
							timeoutConexaoAsClient = val;
						}
					} else {
						erro = 1;
					}
					break;

				case PARAM_TIMEOUT_AS_ACCESS_POINT :
					if ( (aux[0] == '[') && (aux[indAux-1] == ']') ) {
						int val = atoi(&aux[1]);
						if ( (val > 0) && (val<=60) ) {
							timeoutConexaoAsAccessPoint = val;
						}
					} else {
						erro = 1;
					}
					break;

				case PARAM_TIMEOUT_CLIENT_WAITING_SOCKET :
					if ( (aux[0] == '[') && (aux[indAux-1] == ']') ) {
						int val = atoi(&aux[1]);
						if ( (val > 0) && (val<=(5*60)) ) {
							timeoutConnectedAguardandoSocket = val;
						}
					} else {
						erro = 1;
					}
					break;

				default: 
					fim = 1;
					break;
			}
			
			indAux = 0;
		}
	}

	if ( erro ) {
		strSSID[0] = '\0';
		strPasswd[0] = '\0';
	} else {
		Serial.println("\n\n\n=========== Recuperado===========");
		Serial.print("strSSID                          : "); Serial.println(strSSID);
		Serial.print("strPasswd                        : "); Serial.println(strPasswd);
		Serial.print("timeoutConexaoAsClient           : "); Serial.println( timeoutConexaoAsClient, DEC );
		Serial.print("timeoutConexaoAsAccessPoint      : "); Serial.println( timeoutConexaoAsAccessPoint, DEC );
		Serial.print("timeoutConnectedAguardandoSocket : "); Serial.println( timeoutConnectedAguardandoSocket, DEC );
		Serial.println("=================================");
		
	}
}


void hardwareReset( char *msg )
{	int tempo = 1000;

	Serial.println(msg);

	while ( tempo > 0 ) {
		digitalWrite(BUILT_IN_LED, HIGH); // Acende o Led
		delay(tempo); 
		digitalWrite(BUILT_IN_LED, LOW); // Apaga o Led
		delay(tempo); 
		tempo -= 200;
	}

	ESP.restart();
}

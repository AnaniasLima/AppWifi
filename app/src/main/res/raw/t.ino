#include <ESP8266WiFi.h>        // Include the Wi-Fi library
#include <EEPROM.h>

#define BUILT_IN_LED 2


char strMac[30];


WiFiServer wifiServer(80);



#define		FIXED_SSID_ACCESS_POINT		"8266_THERMOMETER"
#define		FIXED_PASSWD_ACCESS_POINT	"nana12345"

#define	ACCESS_POINT	'A'
#define	CLIENT			'C'


// ===== Configuração ===============
#define	MAX_LEN		30

char strSSID[MAX_LEN + 1];
char strPasswd[MAX_LEN + 1];
long timeoutConexaoAsClient      = 20; // segundos
long timeoutConexaoAsAccessPoint = 60 * 10; // segundos

#define	PARAM_CONFIG_SSID				1
#define	PARAM_CONFIG_PASSWD				2
#define	PARAM_TIMEOUT_AS_CLIENT 		3
#define	PARAM_TIMEOUT_AS_ACCESS_POINT 	4

void gravaConfigEEPROM(void);
void recuperaConfigEEPROM(void);
void salvaConfigEEPROM(void);

int operationMode;
char *strErroConexao="";

void setup() {
	char c1, c2;

	pinMode(BUILT_IN_LED, OUTPUT);
	
	Serial.begin(115200);

	recuperaConfigEEPROM();

	if ( startConexao() > 0 ) {
		operationMode=CLIENT;
		wifiServer.begin();
	} else {
		operationMode=ACCESS_POINT;
		wifiServer.begin();
	}

}

void loop()
{
	if ( operationMode == CLIENT ) {
		processaModoClient();
	} else {
		processaModoAccessPoint();
	}
}

void processaModoClient(void)
{
	static int viuAlguem=0;
	long entradaEmModoClient;
	int conta = 0;
	int pisca=0;
	unsigned int mapaLedsModoCliente = 0b00000000001111000000000010101;

	time(&entradaEmModoClient);
    Serial.print("Entrando Modo Cliente. Time="); Serial.println(entradaEmModoClient, DEC);

	while ( ++conta ) {
		if ( (time(NULL) - entradaEmModoClient) > timeoutConexaoAsClient  ) {
			operationMode = ACCESS_POINT;
			break;
		}

		PiscaLed(mapaLedsModoCliente, 30, 100);

		delay(100);
		if ( (conta % 10) == 0 ) {
	    	Serial.print("Modo Cliente. timeout="); Serial.println(timeoutConexaoAsClient - (time(NULL) - entradaEmModoClient), DEC);
		}
	}

    Serial.println("Saindo Modo Cliente.\n");
}

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

void processaModoAccessPoint(void)
{	static int viuAlguem=0;
	int i;
	long now;
	long entradaEmModoAccessPoint;
	char linhaRecebida[100];
	int indRx;
	int conta = 5;
	unsigned int mapaLedsSemConexaoAtiva =  0b11111111110000000000; // 20 bits
	unsigned int mapaLeds1ConexaoAtiva = 0b1100;	// 4 BITS
	unsigned int mapaLedsSocketAtivo = 0b111000111000111000;	// 18 BITS

	time(&entradaEmModoAccessPoint);

	while ( 1 ) {
		
		if ( (time(NULL) - entradaEmModoAccessPoint) > timeoutConexaoAsAccessPoint  ) {
			hardwareReset("Timeout para operar como AccessPoint");
		}

		if ( WiFi.softAPgetStationNum() == 0 ) {
			PiscaLed(mapaLedsSemConexaoAtiva, 20, 100);
			delay(100);
			if ( (conta++ % (5 * 10)) == 0 ) {
				Serial.print("Conexoes ativas : "); Serial.println(WiFi.softAPgetStationNum(), DEC); 
			}
			continue;
		}

		Serial.print("Conexoes ativas : "); Serial.println(WiFi.softAPgetStationNum(), DEC); 

		// Vamos habilitar o Servidor de socket
		WiFiClient client = wifiServer.available();

		Serial.print("Aguardando cliente "); Serial.println((timeoutConexaoAsAccessPoint - (time(NULL) - entradaEmModoAccessPoint)), DEC); 
		Serial.println(client, DEC); 

		if ( client == 0 ) {
			PiscaLed(mapaLeds1ConexaoAtiva, 4, 100);
			delay(100);
			continue;
		}

		if ( client ) {
			indRx = 0;
	    	while ( client.connected() ) {
				PiscaLed(mapaLedsSocketAtivo, 18, 10);
				if ( (time(NULL) - entradaEmModoAccessPoint) > timeoutConexaoAsAccessPoint  ) {
					hardwareReset("Timeout para operar como AccessPoint");
				}
		      	while (client.available() > 0) {
		        	char c = client.read();
		        	if ( indRx >= sizeof(linhaRecebida) ) {
		        		indRx = 0;
		        		continue;
		        	}
					// Processa linha recebida
		        	if ( (c == '\n') || (c == '\r')  ) {
		        		linhaRecebida[indRx] = '\0';
		        		if ( linhaRecebida[0] == '[' ) {
			        		if ( trataLinhaConfiguracao(linhaRecebida) > 0 ) {
			        			char resposta[50];
								uint8_t macAddr[6];
								WiFi.softAPmacAddress(macAddr);
			        			sprintf(resposta, "MAC:[%02x:%02x:%02x:%02x:%02x:%02x]\r\n", macAddr[0], macAddr[1], macAddr[2], macAddr[3], macAddr[4], macAddr[5]);
				        		client.write(resposta);
			        			// reseta processador
								hardwareReset("Nova configuração recebida");
			        		} else {
			        			indRx = 0;
	        			 		Serial.print("Linha Rejeitada : ");
	        			 		Serial.println(linhaRecebida);
			        		}
			        	} else {
			        		// Linhas pequenas ou invalidas são descartadas
		        			indRx = 0;
        			 		Serial.print("Linha Descartada : ");
        			 		Serial.println(linhaRecebida);
			        	}
		        	} else {
		        		// armazena caracter recebido na linha a ser processada
			        	linhaRecebida[indRx++] = c;
			        	linhaRecebida[indRx] = '\0';
		        	}
		      	}
		 
		      	delay(10);
	    	}
	    	
		    client.stop();
		    Serial.println("Client disconnected");
		}
	}
}

//---------------------------------------------------------
// trataLinhaConfiguracao
//---------------------------------------------------------
int trataLinhaConfiguracao(char *linha)
{	int tam = strlen(linha);
	char *p1=NULL;
	char *p2=NULL;
	char *p3=NULL;
	char *p4=NULL;
	char *start;
	char *p;
	int indConfig=0;
	int fim = 0;
	int indLinha=1; // pula '[';
	int erro = 0;

	if ( linha[0] != '[') return(0); 
	if ( tam < 10 ) return(0); 
	if ( linha[tam -1] != ']') return(0); 

	linha[tam -1] = '\0';
	
	p = start = &linha[1];
	tam = 0;
	fim = 0;
	while ( ! fim ) {

		// Anda até achar um TAB ou fim da linha
		if ( (*p != '\t') && ( *p != '\0' ) ) {
			if ( tam++ >= MAX_LEN ) {
				return(0);
			}
			p++;
			continue;
		}
		
		*p++ = '\0';

		indConfig++;
		Serial.print("indConfig = ");  Serial.println(indConfig, DEC);
		Serial.print("start = ");  Serial.println(start);

		// Processa parametro
		switch(indConfig) {
			case PARAM_CONFIG_SSID  			: strcpy(strSSID, start); break;
			case PARAM_CONFIG_PASSWD  			: strcpy(strPasswd, start);  break;
			case PARAM_TIMEOUT_AS_CLIENT  		: timeoutConexaoAsClient = atoi(start); break;
			case PARAM_TIMEOUT_AS_ACCESS_POINT  : timeoutConexaoAsAccessPoint = atoi(start); break;
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

	Serial.print("strSSID                     : "); Serial.println(strSSID);
	Serial.print("strPasswd                   : "); Serial.println(strPasswd);
	Serial.print("timeoutConexaoAsClient      : "); Serial.println( timeoutConexaoAsClient, DEC );
	Serial.print("timeoutConexaoAsAccessPoint : "); Serial.println( timeoutConexaoAsAccessPoint, DEC );

	if ( erro == 0 ) {
		salvaConfigEEPROM();
	}
	return(1);
}

//---------------------------------------------------------
// trataConexaoAsClient
//---------------------------------------------------------
int startConexao(void)
{	int fim = 0;
	long start=0;
	long now=0;

	time(&start);

	if  ( strSSID[0] == '\0' ) {
		strErroConexao = "Não configurado";
	} else {
				
		// Inicialmente vamos tentar conectar na rede configurada
		WiFi.begin(strSSID, strPasswd); 

		fim = 0;
		while( ! fim  ) {
			delay(200);
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

			time(&now);

			if ( (now - start) > timeoutConexaoAsClient ) {
				fim = 1;
			}
		}

		if ( WiFi.status() == WL_CONNECTED) {
			strErroConexao = NULL;
	  		Serial.print("Conectado com sucesso na rede '");
	  		Serial.print(strSSID);
	  		Serial.print("' IP: ");
	  		Serial.println(WiFi.localIP());
	  		return(1); // Conectado a rede configurada OK
		}
		
		switch(WiFi.status()) {
			case WL_NO_SSID_AVAIL 	: strErroConexao = "Não localizou SSID"; break;
			case WL_IDLE_STATUS 	: strErroConexao = "Timeout na conexão"; break;
			case WL_CONNECT_FAILED 	: strErroConexao = "Senha invalida"; break;
			default 				: strErroConexao = "Falha na conexão"; break;
		}
	}

    Serial.println(strErroConexao);

	// Não conseguiu conectar na rede configurada ou ainda não configurada
	// Vamos operar como AccessPoint esperando uma nova configuracao


IPAddress local_IP(192,168,4,1);
IPAddress gateway(192,168,4,1);
IPAddress subnet(255,255,255,0);

Serial.println(WiFi.softAPConfig(local_IP, gateway, subnet) ? "Ready" : "Failed!");

	if ( ! WiFi.softAP(FIXED_SSID_ACCESS_POINT, FIXED_PASSWD_ACCESS_POINT) ) {
		hardwareReset("Falha em  WiFi.softAP");
	}

//	Serial.println(WiFi.localIP());
//	Serial.println("WORKING_AS_ACCESS_POINT");
	
	
	Serial.print("Setting soft-AP configuration ... ");
	Serial.println(WiFi.softAPConfig(local_IP, gateway, subnet) ? "Ready" : "Failed!");
	
	Serial.print("Setting soft-AP ... ");
	Serial.println(WiFi.softAP(FIXED_SSID_ACCESS_POINT, FIXED_PASSWD_ACCESS_POINT) ? "Ready" : "Failed!");
	
	Serial.print("Soft-AP IP address = ");
	Serial.println(WiFi.softAPIP());
	
	Serial.println("WORKING_AS_ACCESS_POINT");
	
	return(0); // funcionando como Access Point
}
	



//---------------------------------------------------------
// salvaConfigEEPROM
//---------------------------------------------------------
void salvaConfigEEPROM(void)
{
	int indEEPROM=0;
	int indConfig=0;
	char *p;
	int fim = 0;
	char aux[30];

	while ( ! fim ) {
		indConfig++;
		p=NULL;
		switch(indConfig) {
			case PARAM_CONFIG_SSID   			: p = strSSID; break;
			case PARAM_CONFIG_PASSWD 			: p = strPasswd; break;
			case PARAM_TIMEOUT_AS_CLIENT 		: sprintf(aux, "%d", timeoutConexaoAsClient); p = aux; break;
			case PARAM_TIMEOUT_AS_ACCESS_POINT 	: sprintf(aux, "%d", timeoutConexaoAsAccessPoint); p = aux; break;
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

#if 0
delay(2000);
Serial.println("OOOIIIII");
while ( indEEPROM < 128 ) {
	char aux[50];
	c = EEPROM.read(indEEPROM++);
	sprintf(aux, "C[%3d] = '%c' - %d", indEEPROM-1, c, c);
	Serial.println(aux);
	delay(20);
}
Serial.println("Aguarde...");
delay(10000);
#endif

#if 0
strSSID[0] = '\0'; 
return;
#endif


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
		Serial.println("\n\n\n=========== Recuperado=======");
		Serial.print("strSSID                     : "); Serial.println(strSSID);
		Serial.print("strPasswd                   : "); Serial.println(strPasswd);
		Serial.print("timeoutConexaoAsClient      : "); Serial.println( timeoutConexaoAsClient, DEC );
		Serial.print("timeoutConexaoAsAccessPoint : "); Serial.println( timeoutConexaoAsAccessPoint, DEC );
		Serial.println("=============================");
		
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

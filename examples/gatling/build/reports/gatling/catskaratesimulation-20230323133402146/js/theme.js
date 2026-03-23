/*
 * Copyright 2011-2014 eBusiness Information, Groupe Excilys (www.ebusinessinformation.fr)
 *
 * Licensed under the Gatling Highcharts License
 */
Highcharts.theme = {
	chart: {
		backgroundColor: '#e6e5e0',
		borderWidth: 0,
		borderRadius: 8,
		plotBackgroundColor: null,
		plotShadow: false,
		plotBorderWidth: 0
	},
	xAxis: {
		gridLineWidth: 0,
		lineColor: '#666',
		tickColor: '#666',
		labels: {
			style: {
				color: '#666'
			}
		},
		title: {
		  style: {
		     color: '#666'
		  }
		}
	},
	yAxis: {
		alternateGridColor: null,
		minorTickInterval: null,
		gridLineColor: '#999',
		lineWidth: 0,
		tickWidth: 0,
		labels: {
			style: {
				color: '#666',
				fontWeight: 'bold'
			}
		},
		title: {
			style: {
				color: '#666',
				font: 'bold 12px Lucida Grande, Lucida Sans Unicode, Verdana, Arial, Helvetica, sans-serif'
			}				
		}
	},
	labels: {
		style: {
			color: '#CCC'
		}
	},
	
	
	rangeSelector: {
		buttonTheme: {
			fill: '#cfc9c6',
			stroke: '#000000',
			style: {
				color: '#34332e',
				fontWeight: 'bold',
				borderColor: '#b2b2a9'
			},
			states: {
				hover: {
					fill: '#92918C',
					stroke: '#000000',
					style: {
				    color: '#34332e',
				    fontWeight: 'bold',
				    borderColor: '#8b897d'
			    }
				},
				select: {
					fill: '#E37400',
					stroke: '#000000',
					style: {
						color: '#FFF'
					}
				}
			}					
		},
		inputStyle: {
			backgroundColor: '#333',
			color: 'silver'
		},
		labelStyle: {
			color: '#8b897d'
		}
	},
	
	navigator: {
		handles: {
			backgroundColor: '#e6e5e0',
			borderColor: '#92918C'
		},
		outlineColor: '#92918C',
		outlineWidth: 1,
		maskFill: 'rgba(146, 145, 140, 0.5)',
		series: {
			color: '#4572A7',
			lineColor: '#4572A7'
		}
	},
	
	scrollbar: {
		buttonBackgroundColor: '#e6e5e0',
		buttonBorderWidth: 1,
		buttonBorderColor: '#92918C',
		buttonArrowColor: '#92918C',
		buttonBorderRadius: 2,

		barBorderWidth: 1,
		barBorderRadius: 0,
		barBackgroundColor: '#92918C',
		barBorderColor: '#92918C',
		
		rifleColor: '#92918C',
		
		trackBackgroundColor: '#b0b0a8',
		trackBorderWidth: 1,
		trackBorderColor: '#b0b0a8'
	}
};

Highcharts.setOptions(Highcharts.theme);
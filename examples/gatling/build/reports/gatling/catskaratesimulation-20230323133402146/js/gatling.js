/*
 * Copyright 2011-2014 eBusiness Information, Groupe Excilys (www.ebusinessinformation.fr)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * 		http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
(function ($) {
	$.fn.expandable = function () {
		var scope = this;

		this.find('.expand-button:not([class*=hidden])').addClass('collapse').on('click', function () {
			var $this = $(this);

			if ($this.hasClass('expand'))
				$this.expand(scope);
			else
				$this.collapse(scope);
		});

		this.find('.expand-all-button').on('click', function () {
			$(this).expandAll(scope);
		});

		this.find('.collapse-all-button').on('click', function () {
			$(this).collapseAll(scope);
		});

		this.collapseAll(this);

		return this;
	};

	$.fn.expand = function (scope, recursive) {
		return this.each(function () {
			var $this = $(this);

			if (recursive) {
				scope.find('.child-of-' + $this.attr('id') + ' .expand-button.expand').expand(scope, true);
				scope.find('.child-of-' + $this.attr('id') + ' .expand-button.collapse').expand(scope, true);
			}

			if ($this.hasClass('expand')) {
				scope.find('.child-of-' + $this.attr('id')).toggle(true);
				$this.toggleClass('expand').toggleClass('collapse');
			}
		});
	};

	$.fn.expandAll = function (scope) {
		$('.child-of-ROOT .expand-button.expand').expand(scope, true);
		$('.child-of-ROOT .expand-button.collapse').expand(scope, true);
	};

	$.fn.collapse = function (scope) {
		return this.each(function () {
			var $this = $(this);

 		    scope.find('.child-of-' + $this.attr('id') + ' .expand-button.collapse').collapse(scope);
			scope.find('.child-of-' + $this.attr('id')).toggle(false);
			$this.toggleClass('expand').toggleClass('collapse');
		});
	};

	$.fn.collapseAll = function (scope) {
		$('.child-of-ROOT .expand-button.collapse').collapse(scope);
	};

	$.fn.sortable = function (target) {
		var table = this;

		this.find('thead .sortable').on('click',  function () {
			var $this = $(this);

			if ($this.hasClass('sorted-down')) {
				var desc = false;
				var style = 'sorted-up';
			}
			else {
				var desc = true;
				var style = 'sorted-down';
			}

			$(target).sortTable($this.attr('id'), desc);

			table.find('thead .sortable').removeClass('sorted-up sorted-down');
			$this.addClass(style);

			return false;
		});

		return this;
	};

	$.fn.sortTable = function (col, desc) {
		function getValue(line) {
			var cell = $(line).find('.' + col);

			if (cell.hasClass('value'))
				var value = cell.text();
			else
				var value = cell.find('.value').text();

			return parseInt(value);
		}

		function sortLines (lines, group) {
            var notErrorTable = col.search("error") == -1;
            var linesToSort = notErrorTable ? lines.filter('.child-of-' + group) : lines;

            var sortedLines = linesToSort.sort(function (a, b) {
				return desc ? getValue(b) - getValue(a): getValue(a) - getValue(b);
			}).toArray();

			var result = [];
			$.each(sortedLines, function (i, line) {
				result.push(line);
                if (notErrorTable)
				    result = result.concat(sortLines(lines, $(line).attr('id')));
			});

			return result;
		}

		this.find('tbody').append(sortLines(this.find('tbody tr').detach(), 'ROOT'));

		return this;
	};
})(jQuery);


